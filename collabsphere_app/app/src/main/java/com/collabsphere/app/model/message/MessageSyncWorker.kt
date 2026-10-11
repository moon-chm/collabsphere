package com.collabsphere.app.model.message
import com.collabsphere.app.model.OutboxSyncStatus
import com.collabsphere.app.model.SyncPolicy
import android.util.Log

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.dto.message.MessageRequest
import com.collabsphere.app.remote.message.MessageApiService
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
        val messageId = inputData.getInt("MESSAGE_ID", 0)
        val userId = inputData.getInt("USER_ID", -1)
        val workspaceIdParam = inputData.getInt("WORKSPACE_ID", -1)
        val channelIdParam = inputData.getInt("CHANNEL_ID", -1)

        if (messageId == 0 || userId == -1 || workspaceIdParam == -1 || channelIdParam == -1) {
            return@withContext Result.failure()
        }

        // Account isolation guard: if the currently authenticated user in DataStore is different
        // from this work item's owner, abort immediately to prevent cross-account payload contamination.
        val authenticatedUserId = dataStore.data
            .map { it[UserPreferences.USER_ID] ?: -1 }
            .first()
        if (authenticatedUserId != -1 && authenticatedUserId != userId) {
            Log.w(
                "MessageSyncWorker",
                "Aborting work item $messageId — payload userId=$userId but authenticated userId=$authenticatedUserId"
            )
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
                val status = apiService.deleteMessage(messageId, userId, workspaceId, channelId)
                return@withContext SyncPolicy.toWorkResult(SyncPolicy.forStatus(status.value, isDelete = true))
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
                status = status,
                replyToId = inputData.getInt("REPLY_TO_ID", 0).takeIf { it > 0 },
                mediaUrl = inputData.getString("MEDIA_URL"),
                idempotencyKey = messageDao.getMessageById(messageId)?.clientMutationId
                    ?: inputData.getString("IDEMPOTENCY_KEY")
                    ?: "message-local-${userId}-$messageId"
            )

            if (actionType == "UPDATE") {
                apiService.updateMessage(messageId, request)
            } else {
                val remoteResponse = apiService.createMessage(request)
                if (messageId != remoteResponse.id) {
                    messageDao.replaceTempId(messageId, remoteResponse.id)
                }
                dataStore.edit { it.remove(stringPreferencesKey("message_idempotency_$messageId")) }
            }
            return@withContext Result.success()

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("MessageSyncWorker", "Operation failed", e)
            val decision = SyncPolicy.forFailure(e, isDelete = actionType == "DELETE")
            if (decision == com.collabsphere.app.model.SyncDecision.DROP) {
                messageDao.updateSyncStatus(messageId, OutboxSyncStatus.FAILED_PERMANENT)
            }
            return@withContext SyncPolicy.toWorkResult(decision)
        }
    }
}
