package com.example.rohit_project_challlange.model.message

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.*
import com.example.rohit_project_challlange.dto.message.MessageRequest
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
        private val LAST_SYNC_KEY = longPreferencesKey("messages_last_sync_time")
    }

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
            e.printStackTrace()
            val localId = messageDao.sendMessage(message)
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
            e.printStackTrace()
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
                e.printStackTrace()
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
            e.printStackTrace()
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
        while (isActive) {
            try {
                val lastSyncTime = dataStore.data.map { it[LAST_SYNC_KEY] ?: 0L }.first()
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
                        preferences[LAST_SYNC_KEY] = newestTimestamp
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(3000)
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

        workManager.enqueueUniqueWork(
            "MESSAGE_SYNC_QUEUE",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }
}