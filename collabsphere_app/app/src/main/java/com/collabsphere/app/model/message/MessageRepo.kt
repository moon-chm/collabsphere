package com.collabsphere.app.model.message
import android.util.Log

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.*
import com.collabsphere.app.dto.message.MessageRequest
import com.collabsphere.app.dto.message.MessageSyncDto
import com.collabsphere.app.model.TempId
import com.collabsphere.app.AppConfig
import com.collabsphere.app.dto.message.ChannelReactionSummary
import com.collabsphere.app.dto.message.ChannelReadState
import com.collabsphere.app.remote.dm.DmApiService
import com.collabsphere.app.remote.dm.DmWebSocketService
import com.collabsphere.app.remote.message.MessageApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MessageRepo(
    private val messageDao: MessageDao,
    private val apiService: MessageApiService,
    private val workManager: WorkManager,
    private val dataStore: DataStore<Preferences>,
    private val dmApiService: DmApiService
) {
    companion object {
        private const val LAST_SYNC_KEY_PREFIX = "messages_last_sync_time_"
        private const val SOCKET_FALLBACK_POLL_MS = 30_000L
        const val HISTORY_PAGE_SIZE = 50
    }

    private fun getSyncKey(workspaceId: Int, channelId: Int) =
        longPreferencesKey("${LAST_SYNC_KEY_PREFIX}${workspaceId}_$channelId")

    // MessageRepo is a Koin singleton shared by every MessageViewModel instance — without this
    // guard, re-entering the same channel (without popping the earlier backstack entry) starts a
    // second independent 3s poller against the same endpoint.
    private val activeSyncLoops = java.util.concurrent.ConcurrentHashMap.newKeySet<Pair<Int, Int>>()

    suspend fun sendMessageToUser(message: MessageEntity): Long = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = MessageRequest(
                id = message.id,
                userId = message.userId,
                workspaceId = message.workspaceId,
                channelId = message.channelId,
                userName = message.userName,
                content = message.content,
                status = message.status.name,
                replyToId = message.replyToId,
                mediaUrl = message.mediaUrl
            )
            val remoteMessage = apiService.createMessage(request)
            val updatedMessage = message.copy(id = remoteMessage.id)
            messageDao.sendMessage(updatedMessage)
        } catch (e: Exception) {
            Log.e("MessageRepo", "Operation failed", e)
            // Draw the placeholder id from the negative range — Room autoGenerate only kicks in for
            // id == 0, so a positive fallback here could collide with a real id synced down later.
            val localId = messageDao.sendMessage(message.copy(id = TempId.next()))
            val syncData = workDataOf(
                "ACTION_TYPE" to "CREATE",
                "REPLY_TO_ID" to (message.replyToId ?: 0),
                "MEDIA_URL" to message.mediaUrl,
                "MESSAGE_ID" to localId.toInt(),
                "USER_ID" to message.userId,
                "WORKSPACE_ID" to message.workspaceId,
                "CHANNEL_ID" to message.channelId,
                "USER_NAME" to message.userName,
                "CONTENT" to message.content,
                "STATUS" to message.status.name
            )
            enqueueSync(syncData)
            localId
        }
    }

    // Offline-first: emit cached Room data immediately, delta loop keeps it fresh in background.
    fun getMessage(workspaceId: Int, channelId: Int): Flow<List<MessageEntity>> =
        messageDao.getMessageForChannels(workspaceId, channelId)

    suspend fun deleteMessage(messageId: Int, userId: Int, workspaceId: Int, channelId: Int): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiSuccess = apiService.deleteMessage(messageId, userId, workspaceId, channelId)
                val deletedRows = messageDao.deleteMessage(messageId, userId, workspaceId, channelId)
                deletedRows > 0 || apiSuccess
            } catch (e: Exception) {
                Log.e("MessageRepo", "Operation failed", e)
                val deletedRows = messageDao.deleteMessage(messageId, userId, workspaceId, channelId)
                val syncData = workDataOf(
                    "ACTION_TYPE" to "DELETE",
                    "MESSAGE_ID" to messageId,
                    "USER_ID" to userId,
                    "WORKSPACE_ID" to workspaceId,
                    "CHANNEL_ID" to channelId
                )
                enqueueSync(syncData)
                deletedRows > 0
            }
        }

    suspend fun updateMessage(message: MessageEntity) = withContext(Dispatchers.IO) {
        try {
            val request = MessageRequest(
                id = message.id,
                userId = message.userId,
                workspaceId = message.workspaceId,
                channelId = message.channelId,
                userName = message.userName,
                content = message.content,
                status = message.status.name
            )
            apiService.updateMessage(messageId = message.id, request = request)
            messageDao.updateMessage(message)
        } catch (e: Exception) {
            Log.e("MessageRepo", "Operation failed", e)
            messageDao.updateMessage(message)
            val syncData = workDataOf(
                "ACTION_TYPE" to "UPDATE",
                "MESSAGE_ID" to message.id,
                "USER_ID" to message.userId,
                "WORKSPACE_ID" to message.workspaceId,
                "CHANNEL_ID" to message.channelId,
                "USER_NAME" to message.userName,
                "CONTENT" to message.content,
                "STATUS" to message.status.name
            )
            enqueueSync(syncData)
        }
    }

    suspend fun applyRealtimeChange(remote: MessageSyncDto) = withContext(Dispatchers.IO) {
        try {
            if (remote.isDeleted) {
                messageDao.applyDelta(emptyList(), listOf(remote.id))
            } else if (isWithinLoadedWindow(remote)) {
                messageDao.applyDelta(listOf(remote.toEntity()), emptyList())
            }
        } catch (e: Exception) {
            Log.e("MessageRepo", "Realtime apply failed", e)
        }
    }

    private suspend fun isWithinLoadedWindow(remote: MessageSyncDto): Boolean {
        val oldestLoaded = messageDao.oldestSyncedMessageId(remote.workspaceId, remote.channelId)
        return oldestLoaded == null || remote.id >= oldestLoaded
    }

    suspend fun sendMediaMessage(message: MessageEntity, fileBytes: ByteArray, mimeType: String, fileName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val mediaUrl = dmApiService.uploadDmMedia(AppConfig.BASE_URL, fileBytes, mimeType, fileName)
                sendMessageToUser(message.copy(mediaUrl = mediaUrl))
                Unit
            }
        }

    suspend fun markRead(workspaceId: Int, channelId: Int, lastReadMessageId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { apiService.markRead(workspaceId, channelId, lastReadMessageId) }
    }

    suspend fun fetchReadStates(workspaceId: Int, channelId: Int): Result<List<ChannelReadState>> = withContext(Dispatchers.IO) {
        runCatching { apiService.getReadStates(workspaceId, channelId) }
    }

    suspend fun setPinned(messageId: Int, pinned: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.setPinned(messageId, pinned)
            messageDao.updatePinnedAt(messageId, if (pinned) System.currentTimeMillis() else null)
        }
    }

    suspend fun fetchPinned(workspaceId: Int, channelId: Int): Result<List<MessageEntity>> = withContext(Dispatchers.IO) {
        runCatching { apiService.getPinned(workspaceId, channelId).map { it.toEntity() } }
    }

    suspend fun toggleReaction(messageId: Int, emoji: String, add: Boolean): Result<ChannelReactionSummary> =
        withContext(Dispatchers.IO) {
            runCatching { apiService.toggleReaction(messageId, emoji, add) }
        }

    suspend fun fetchReactions(workspaceId: Int, channelId: Int): Result<List<ChannelReactionSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fromId = messageDao.oldestSyncedMessageId(workspaceId, channelId) ?: 0
                apiService.getReactions(workspaceId, channelId, fromId)
            }
        }

    suspend fun sendTyping(workspaceId: Int, channelId: Int, isTyping: Boolean) {
        if (!DmWebSocketService.isWebSocketConnected) return
        runCatching { dmApiService.sendChannelTyping(workspaceId, channelId, isTyping) }
    }

    suspend fun loadOlderMessages(workspaceId: Int, channelId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val oldestLoaded = messageDao.oldestSyncedMessageId(workspaceId, channelId)
            val page = apiService.getMessageHistory(workspaceId, channelId, oldestLoaded, HISTORY_PAGE_SIZE)
            messageDao.applyDelta(page.map { it.toEntity() }, emptyList())
            Result.success(page.size >= HISTORY_PAGE_SIZE)
        } catch (e: Exception) {
            Log.e("MessageRepo", "Loading older messages failed", e)
            Result.failure(e)
        }
    }

    private suspend fun loadInitialPage(workspaceId: Int, channelId: Int) {
        val page = apiService.getMessageHistory(workspaceId, channelId, null, HISTORY_PAGE_SIZE)
        messageDao.applyDelta(page.map { it.toEntity() }, emptyList())
        val syncKey = getSyncKey(workspaceId, channelId)
        dataStore.edit { preferences ->
            preferences[syncKey] = page.maxOfOrNull { it.updatedAt } ?: 1L
        }
    }

    private fun MessageSyncDto.toEntity() = MessageEntity(
        id = id,
        userId = userId,
        workspaceId = workspaceId,
        channelId = channelId,
        userName = userName,
        content = content,
        status = MessageStatus.valueOf(status),
        replyToId = replyToId,
        mediaUrl = mediaUrl,
        pinnedAt = pinnedAt
    )

    suspend fun startDeltaSyncLoop(workspaceId: Int, channelId: Int) = withContext(Dispatchers.IO) {
        val loopKey = workspaceId to channelId
        if (!activeSyncLoops.add(loopKey)) return@withContext
        try {
        var lastPollAt = 0L
        var wasSocketConnected = false
        while (isActive) {
            com.collabsphere.app.MyApplication.isAppForegroundFlow.first { it }
            val socketConnected = DmWebSocketService.isWebSocketConnected
            val justReconnected = socketConnected && !wasSocketConnected
            wasSocketConnected = socketConnected
            val pollDue = !socketConnected || justReconnected ||
                System.currentTimeMillis() - lastPollAt >= SOCKET_FALLBACK_POLL_MS
            if (pollDue) {
                lastPollAt = System.currentTimeMillis()
                try {
                    val syncKey = getSyncKey(workspaceId, channelId)
                    val lastSyncTime = dataStore.data.map { it[syncKey] ?: 0L }.first()
                    if (lastSyncTime == 0L) {
                        loadInitialPage(workspaceId, channelId)
                        delay(2000)
                        continue
                    }
                    val updates = apiService.getMessageUpdates(workspaceId, channelId, lastSyncTime)

                    if (updates.isNotEmpty()) {
                        val oldestLoaded = messageDao.oldestSyncedMessageId(workspaceId, channelId)
                        val upserts = updates
                            .filter { !it.isDeleted && (oldestLoaded == null || it.id >= oldestLoaded) }
                            .map { it.toEntity() }
                        val deletes = updates.filter { it.isDeleted }.map { it.id }
                        messageDao.applyDelta(upserts, deletes)

                        val newestTimestamp = updates.maxOf { it.updatedAt }
                        dataStore.edit { preferences ->
                            preferences[syncKey] = newestTimestamp
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MessageRepo", "Operation failed", e)
                }
            }
            delay(2000)
        }
        } finally {
            activeSyncLoops.remove(loopKey)
        }
    }

    private fun enqueueSync(data: Data) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<MessageSyncWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        // Unique per message, not per entity TYPE — see ChannelRepo.enqueueSync for why a shared
        // name across every message would let one permanently-failed sync cancel every other one's queue.
        val messageId = data.getInt("MESSAGE_ID", 0)
        workManager.enqueueUniqueWork(
            "MESSAGE_SYNC_$messageId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }
}