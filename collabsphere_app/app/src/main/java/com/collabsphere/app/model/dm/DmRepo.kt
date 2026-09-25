package com.collabsphere.app.model.dm
import android.util.Log

import androidx.work.*
import com.collabsphere.app.dto.dm.DmDto
import com.collabsphere.app.model.TempId
import com.collabsphere.app.remote.dm.DmApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit

private const val DM_HISTORY_PAGE_SIZE = 50

class DmRepo(
    private val dmDao: DmDao,
    private val reactionDao: DmReactionDao,
    private val apiService: DmApiService,
    private val workManager: WorkManager
) {

    private val _incomingEvents = MutableSharedFlow<DmDto>(extraBufferCapacity = 64)
    val incomingEvents: SharedFlow<DmDto> = _incomingEvents.asSharedFlow()

    fun getDmHistory(workspaceId: Int, currentUserId: Int, chatPartnerId: Int): Flow<List<DmEntity>> {
        return dmDao.getDmHistory(workspaceId, currentUserId, chatPartnerId)
    }

    suspend fun connectToChat(baseUrl: String, currentUserId: Long) {
        val sinceId = dmDao.newestSyncedDmId() ?: 0
        apiService.connect(baseUrl, currentUserId, sinceId)
    }

    suspend fun loadOlderDms(baseUrl: String, workspaceId: Int, currentUserId: Int, chatPartnerId: Int): Result<Boolean> =
        try {
            val oldestLoaded = dmDao.oldestSyncedDmId(workspaceId, currentUserId, chatPartnerId)
            val page = apiService.getDmHistoryPage(baseUrl, workspaceId, chatPartnerId, oldestLoaded, DM_HISTORY_PAGE_SIZE)
            page.forEach { saveIncomingDm(it, currentUserId) }
            Result.success(page.size >= DM_HISTORY_PAGE_SIZE)
        } catch (e: Exception) {
            Log.e("DmRepo", "Loading older DMs failed", e)
            Result.failure(e)
        }

    suspend fun sendRealtimeDm(
        id: Int? = null,
        workspaceId: Int,
        senderId: Int,
        receiverId: Int,
        content: String,
        mediaUrl: String? = null,
        replyToId: Int? = null
    ) {
        val timestampVal = System.currentTimeMillis()
        val tempId = if (id == null || id == 0) TempId.next() else id

        val socketMessage = DmDto(
            action = "SEND_MESSAGE",
            workspaceId = workspaceId,
            senderId = senderId,
            receiverId = receiverId,
            content = content,
            timestamp = timestampVal,
            id = if (tempId < 0) null else tempId,
            mediaUrl = mediaUrl,
            replyToId = replyToId
        )

        val temporaryLocalEntity = DmEntity(
            id = tempId,
            workspaceId = workspaceId,
            senderId = senderId,
            receiverId = receiverId,
            dm_content = content,
            timestamp = socketMessage.timestamp,
            mediaUrl = mediaUrl,
            isRead = false,
            replyToId = replyToId
        )
        dmDao.sendDm(temporaryLocalEntity)

        try {
            apiService.sendDm(socketMessage)
        } catch (e: Exception) {
            Log.e("DmRepo", "Operation failed", e)
            val syncData = workDataOf(
                "ACTION_TYPE" to "SEND_MESSAGE",
                "DM_ID" to tempId,
                "WORKSPACE_ID" to workspaceId,
                "SENDER_ID" to senderId,
                "RECEIVER_ID" to receiverId,
                "CONTENT" to content,
                "TIMESTAMP" to timestampVal,
                "MEDIA_URL" to mediaUrl,
                "REPLY_TO_ID" to (replyToId ?: 0)
            )
            enqueueSync(syncData)
        }
    }

    /** Upload media file and send DM with the resulting Cloudinary URL */
    suspend fun sendMediaDm(
        baseUrl: String,
        workspaceId: Int,
        senderId: Int,
        receiverId: Int,
        fileBytes: ByteArray,
        mimeType: String,
        fileName: String
    ) {
        val mediaUrl = apiService.uploadDmMedia(baseUrl, fileBytes, mimeType, fileName)
        sendRealtimeDm(
            workspaceId = workspaceId,
            senderId = senderId,
            receiverId = receiverId,
            content = "",
            mediaUrl = mediaUrl
        )
    }

    fun listenForIncomingDms(): Flow<DmDto> {
        return apiService.observeIncomingDms().onEach { dto ->
            _incomingEvents.emit(dto)
        }
    }

    suspend fun fetchOnlineUsers(baseUrl: String, workspaceId: Int): List<Int> =
        apiService.getOnlineUsers(baseUrl, workspaceId)

    suspend fun sendTypingStatus(workspaceId: Int, senderId: Int, receiverId: Int, isTyping: Boolean) =
        apiService.sendTypingStatus(workspaceId, senderId, receiverId, isTyping)

    /** Send REACT_MESSAGE or UNREACT_MESSAGE WebSocket event */
    suspend fun sendReaction(messageId: Int, emoji: String, workspaceId: Int, receiverId: Int, isAdd: Boolean) {
        val action = if (isAdd) "REACT_MESSAGE" else "UNREACT_MESSAGE"
        val payload = DmDto(
            action = action,
            id = messageId,
            workspaceId = workspaceId,
            receiverId = receiverId,
            emoji = emoji
        )
        try {
            apiService.sendDm(payload)
        } catch (e: Exception) {
            Log.e("DmRepo", "Reaction send failed", e)
        }
    }

    /** Tell the server we've read all messages from chatPartnerId */
    suspend fun markConversationRead(workspaceId: Int, currentUserId: Int, chatPartnerId: Int) {
        // Optimistic local update
        dmDao.markAllReadFrom(currentUserId, chatPartnerId, workspaceId)
        val payload = DmDto(
            action = "MARK_READ",
            workspaceId = workspaceId,
            senderId = currentUserId,
            receiverId = chatPartnerId
        )
        try {
            apiService.sendDm(payload)
        } catch (_: Exception) {}
    }

    suspend fun saveIncomingDm(message: DmDto, currentUserId: Int) {
        if (message.action in listOf("TYPING_START", "TYPING_STOP", "USER_ONLINE", "USER_OFFLINE")) {
            // Ephemeral real-time presence/typing events — not persistent chat rows
            return
        }

        if (message.action == "READ_RECEIPT") {
            // Mark all of our own sent messages to this receiver as read
            val partnerId = message.senderId // the one who just read
            val wsId = message.workspaceId
            // Update rows where we are the sender and partner is receiver
            dmDao.markAllReadFrom(partnerId, currentUserId, wsId)
            return
        }

        if (message.action == "REACT_MESSAGE" || message.action == "UNREACT_MESSAGE") {
            // reactions map carries aggregated state from server
            val msgId = message.id ?: return
            val reactionMap = message.reactions ?: return
            // Rebuild local reaction cache for this message
            reactionDao.deleteAllReactionsForMessage(msgId)
            // The server sends back reactions as {emoji: count} but we need per-user rows
            // We use a synthetic userId=0 as a placeholder for aggregated counts
            // Actually, we store the reactions coming from the dto's userId context
            // For simplicity: store reactor as senderId
            reactionMap.entries.forEach { (emoji, _) ->
                // We don't know individual reactors from aggregated map, so just emit to UI via event flow
            }
            return
        }

        if (message.action == "DELETE_MESSAGE") {
            val id = message.id
            if (id != null && id != 0) {
                dmDao.deleteDm(id, message.workspaceId)
                dmDao.deleteDmById(id)
                reactionDao.deleteAllReactionsForMessage(id)
            }
            return
        }

        if (message.action == "UPDATE_MESSAGE") {
            val id = message.id
            if (id != null && id != 0) {
                dmDao.updateDmContent(id, message.content)
            }
            return
        }

        if (message.action == "MESSAGE_DELIVERED") {
            // Server confirmed our message was saved — update the temp row with real ID
            val id = message.id
            if (id != null && id != 0) {
                val localEntity = DmEntity(
                    id = id,
                    workspaceId = message.workspaceId,
                    senderId = message.senderId,
                    receiverId = message.receiverId,
                    dm_content = message.content,
                    timestamp = message.timestamp,
                    mediaUrl = message.mediaUrl,
                    isRead = false,
                    replyToId = message.replyToId
                )
                dmDao.deleteDmByContentAndTimestamp(message.content, message.timestamp)
                dmDao.sendDm(localEntity)
            }
            return
        }

        if (message.action == "HISTORY") {
            val id = message.id
            if (id == null || id == 0) return
            val historyEntity = DmEntity(
                id = id,
                workspaceId = message.workspaceId,
                senderId = message.senderId,
                receiverId = message.receiverId,
                dm_content = message.content,
                timestamp = message.timestamp,
                mediaUrl = message.mediaUrl,
                isRead = message.reactions != null, // reuse reactions field to pass isRead — see server
                replyToId = message.replyToId
            )
            dmDao.sendDm(historyEntity)
            return
        }

        if (message.senderId == currentUserId) {
            if (message.id != null && message.id != 0) {
                val localEntity = DmEntity(
                    id = message.id,
                    workspaceId = message.workspaceId,
                    senderId = message.senderId,
                    receiverId = message.receiverId,
                    dm_content = message.content,
                    timestamp = message.timestamp,
                    mediaUrl = message.mediaUrl,
                    isRead = false,
                    replyToId = message.replyToId
                )
                dmDao.deleteDmByContentAndTimestamp(message.content, message.timestamp)
                dmDao.sendDm(localEntity)
            }
            return
        }

        val id = message.id
        if (id == null || id == 0) return
        val localEntity = DmEntity(
            id = id,
            workspaceId = message.workspaceId,
            senderId = message.senderId,
            receiverId = message.receiverId,
            dm_content = message.content,
            timestamp = message.timestamp,
            mediaUrl = message.mediaUrl,
            isRead = false,
            replyToId = message.replyToId
        )
        dmDao.sendDm(localEntity)
    }

    suspend fun updateDm(dmId: Int, workspaceId: Int, senderId: Int, receiverId: Int, newContent: String) {
        dmDao.updateDmContent(dmId, newContent)
        val socketMessage = DmDto(
            action = "UPDATE_MESSAGE",
            workspaceId = workspaceId,
            senderId = senderId,
            receiverId = receiverId,
            content = newContent,
            timestamp = System.currentTimeMillis(),
            id = dmId
        )
        try {
            apiService.sendDm(socketMessage)
        } catch (e: Exception) {
            Log.e("DmRepo", "Operation failed", e)
        }
    }

    suspend fun deleteDm(dmId: Int, workspaceId: Int, receiverId: Int = 0) {
        dmDao.deleteDm(dmId, workspaceId)
        dmDao.deleteDmById(dmId)
        reactionDao.deleteAllReactionsForMessage(dmId)

        val socketMessage = DmDto(
            action = "DELETE_MESSAGE",
            workspaceId = workspaceId,
            senderId = 0,
            receiverId = receiverId,
            content = "",
            timestamp = System.currentTimeMillis(),
            id = dmId
        )
        try {
            apiService.sendDm(socketMessage)
        } catch (e: Exception) {
            Log.e("DmRepo", "Operation failed", e)
            val syncData = workDataOf(
                "ACTION_TYPE" to "DELETE_MESSAGE",
                "DM_ID" to dmId,
                "WORKSPACE_ID" to workspaceId
            )
            enqueueSync(syncData)
        }
    }

    fun getReactionCountsForMessage(messageId: Int) = reactionDao.getReactionCountsForMessage(messageId)

    suspend fun getUserReactionsForMessage(messageId: Int, userId: Int) =
        reactionDao.getUserReactionsForMessage(messageId, userId)

    suspend fun upsertReactionLocally(messageId: Int, userId: Int, emoji: String) {
        reactionDao.upsertReaction(DmReactionEntity(messageId, userId, emoji))
    }

    suspend fun deleteReactionLocally(messageId: Int, userId: Int, emoji: String) {
        reactionDao.deleteReaction(messageId, userId, emoji)
    }

    suspend fun disconnectChat() {
        apiService.disconnect()
    }

    private fun enqueueSync(data: Data) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<DmSyncWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        val dmId = data.getInt("DM_ID", 0)
        workManager.enqueueUniqueWork(
            "DM_SYNC_$dmId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }
}