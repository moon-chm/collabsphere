package com.example.rohit_project_challlange.model.dm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rohit_project_challlange.dto.dm.DmDto
import com.example.rohit_project_challlange.remote.dm.DmApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DmSyncWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val apiService: DmApiService,
    private val dataStore: DataStore<Preferences>
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val actionType = inputData.getString("ACTION_TYPE") ?: "SEND_MESSAGE"
        val dmId = inputData.getInt("DM_ID", -1)
        val workspaceIdParam = inputData.getInt("WORKSPACE_ID", -1)

        if (workspaceIdParam == -1) return@withContext Result.failure()

        val workspaceId = if (workspaceIdParam < 0) {
            val mappingKey = intPreferencesKey("temp_ws_$workspaceIdParam")
            dataStore.data.map { it[mappingKey] ?: workspaceIdParam }.first()
        } else {
            workspaceIdParam
        }

        try {
            val senderId = inputData.getInt("SENDER_ID", -1)
            val userIdFlowKey = intPreferencesKey("saved_user_id")
            val savedUserId = dataStore.data.map { it[userIdFlowKey] ?: -1 }.first()
            val connectUserId = if (senderId != -1) senderId else savedUserId

            if (connectUserId != -1) {
                apiService.connect("http://10.238.3.93:8080", connectUserId.toLong())
            }

            when (actionType) {
                "DELETE_MESSAGE" -> {
                    if (dmId == -1) return@withContext Result.failure()
                    val socketMessage = DmDto(
                        action = "DELETE_MESSAGE",
                        workspaceId = workspaceId,
                        senderId = 0,
                        receiverId = 0,
                        content = "",
                        timestamp = System.currentTimeMillis(),
                        id = dmId
                    )
                    apiService.sendDm(socketMessage)
                }
                "SEND_MESSAGE" -> {
                    val receiverId = inputData.getInt("RECEIVER_ID", -1)
                    val content = inputData.getString("CONTENT") ?: ""

                    if (senderId == -1 || receiverId == -1) return@withContext Result.failure()

                    val socketMessage = DmDto(
                        action = "SEND_MESSAGE",
                        workspaceId = workspaceId,
                        senderId = senderId,
                        receiverId = receiverId,
                        content = content,
                        timestamp = System.currentTimeMillis(),
                        id = if (dmId == -1 || dmId == 0 || dmId < 0) null else dmId
                    )
                    apiService.sendDm(socketMessage)
                }
            }
            return@withContext Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.retry()
        }
    }
}