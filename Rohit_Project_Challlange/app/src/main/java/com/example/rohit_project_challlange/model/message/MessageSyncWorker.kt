package com.example.rohit_project_challlange.model.message

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rohit_project_challlange.dto.message.MessageRequest
import com.example.rohit_project_challlange.remote.message.MessageApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MessageSyncWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val apiService: MessageApiService,
    private val messageDao: MessageDao,
    private val dataStore: DataStore<Preferences>
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val actionType = inputData.getString("ACTION_TYPE") ?: "CREATE"
        val messageId = inputData.getInt("MESSAGE_ID", -1)
        val userId = inputData.getInt("USER_ID", -1)
        val workspaceIdParam = inputData.getInt("WORKSPACE_ID", -1)
        val channelIdParam = inputData.getInt("CHANNEL_ID", -1)

        if (messageId == -1 || userId == -1 || workspaceIdParam == -1 || channelIdParam == -1) {
            return@withContext Result.failure()
        }

        val workspaceId = if (workspaceIdParam < 0) {
            val mappingKey = intPreferencesKey("temp_ws_$workspaceIdParam")
            dataStore.data.map { it[mappingKey] ?: workspaceIdParam }.first()
        } else {
            workspaceIdParam
        }

        val channelId = if (channelIdParam < 0) {
            val mappingKey = intPreferencesKey("temp_ch_$channelIdParam")
            dataStore.data.map { it[mappingKey] ?: channelIdParam }.first()
        } else {
            channelIdParam
        }

        try {
            if (actionType == "DELETE") {
                apiService.deleteMessage(messageId, userId, workspaceId, channelId)
                return@withContext Result.success()
            }

            val userName = inputData.getString("USER_NAME") ?: ""
            val content = inputData.getString("CONTENT") ?: ""
            val status = inputData.getString("STATUS") ?: ""

            val request = MessageRequest(
                id = messageId,
                userId = userId,
                workspaceId = workspaceId,
                channelId = channelId,
                userName = userName,
                content = content,
                status = status
            )

            if (actionType == "UPDATE") {
                apiService.updateMessage(messageId, request)
            } else {
                val remoteResponse = apiService.createMessage(request)
                if (messageId != remoteResponse.id) {
                    messageDao.updateMessageId(messageId, remoteResponse.id)
                }
            }
            return@withContext Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.retry()
        }
    }
}