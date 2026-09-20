package com.example.rohit_project_challlange.model.message
import android.util.Log

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.*
import com.example.rohit_project_challlange.dto.message.MessageRequest
import com.example.rohit_project_challlange.model.TempId
import com.example.rohit_project_challlange.remote.message.MessageApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MessageRepo(
    private val messageDao: MessageDao,
    private val apiService: MessageApiService,
    private val workManager: WorkManager,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private const val LAST_SYNC_KEY_PREFIX = "messages_last_sync_time_"
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
                status = message.status.name
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

    fun getMessage(workspaceId: Int, channelId: Int): Flow<List<MessageEntity>> = flow {
        try {
            val remoteMessage = apiService.getMessageByuser(workspaceId, channelId)
            val messageEntity = remoteMessage.map { remote ->
                MessageEntity(
                    id = remote.id,
                    userId = remote.userId,
                    workspaceId = remote.workspaceId,
                    channelId = remote.channelId,
                    userName = remote.userName,
                    content = remote.content,
                    status = MessageStatus.valueOf(remote.status)
                )
            }
            messageDao.insertAllMessage(messageEntity)
        } catch (e: Exception) {
            Log.e("MessageRepo", "Operation failed", e)
        }
        emitAll(messageDao.getMessageForChannels(workspaceId, channelId))
    }.flowOn(Dispatchers.IO)

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

    suspend fun startDeltaSyncLoop(workspaceId: Int, channelId: Int) = withContext(Dispatchers.IO) {
        val loopKey = workspaceId to channelId
        if (!activeSyncLoops.add(loopKey)) return@withContext
        try {
        while (isActive) {
            com.example.rohit_project_challlange.MyApplication.isAppForegroundFlow.first { it }
            try {
                val syncKey = getSyncKey(workspaceId, channelId)
                val lastSyncTime = dataStore.data.map { it[syncKey] ?: 0L }.first()
                val updates = apiService.getMessageUpdates(workspaceId, channelId, lastSyncTime)

                if (updates.isNotEmpty()) {
                    updates.forEach { remote ->
                        if (remote.isDeleted) {
                            messageDao.deleteMessage(
                                messageId = remote.id,
                                userId = remote.userId,
                                workspaceId = remote.workspaceId,
                                channelId = remote.channelId
                            )
                        } else {
                            val entity = MessageEntity(
                                id = remote.id,
                                userId = remote.userId,
                                workspaceId = remote.workspaceId,
                                channelId = remote.channelId,
                                userName = remote.userName,
                                content = remote.content,
                                status = MessageStatus.valueOf(remote.status)
                            )
                            messageDao.sendMessage(entity)
                        }
                    }

                    val newestTimestamp = updates.maxOf { it.updatedAt }
                    dataStore.edit { preferences ->
                        preferences[syncKey] = newestTimestamp
                    }
                }
            } catch (e: Exception) {
                Log.e("MessageRepo", "Operation failed", e)
            }
            delay(1000)
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