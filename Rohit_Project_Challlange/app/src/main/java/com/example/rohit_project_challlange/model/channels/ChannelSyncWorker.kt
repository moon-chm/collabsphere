package com.example.rohit_project_challlange.model.channels

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rohit_project_challlange.dto.channel.ChannelRequest
import com.example.rohit_project_challlange.remote.channel.ChannelApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ChannelSyncWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val apiService: ChannelApiService,
    private val channelDao: ChannelDao,
    private val dataStore: DataStore<Preferences>
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val actionType = inputData.getString("ACTION_TYPE") ?: "CREATE"
        val workspaceIdParam = inputData.getInt("WORKSPACE_ID", -1)
        val channelName = inputData.getString("CHANNEL_NAME") ?: return@withContext Result.failure()
        val userId = inputData.getInt("USER_ID", -1)

        if (workspaceIdParam == -1) return@withContext Result.failure()

        val workspaceId = if (workspaceIdParam < 0) {
            val mappingKey = intPreferencesKey("temp_ws_$workspaceIdParam")
            dataStore.data.map { it[mappingKey] ?: workspaceIdParam }.first()
        } else {
            workspaceIdParam
        }

        try {
            if (actionType == "DELETE") {
                apiService.deleteChannel(channelName, workspaceId, if (userId == 0) null else userId)
                return@withContext Result.success()
            }

            val description = inputData.getString("DESCRIPTION") ?: ""
            val request = ChannelRequest(
                id = 0,
                userId = if (userId == -1) null else userId,
                channelName = channelName,
                workspaceId = workspaceId,
                description = description
            )
            val remoteResponse = apiService.createChannel(request)
            val tempChannelId = inputData.getInt("CHANNEL_ID", -1)
            if (tempChannelId != -1) {
                val localChannelId = channelDao.getChannelIdByName(channelName, workspaceId)
                if (localChannelId != null && localChannelId != remoteResponse.id) {
                    channelDao.swapChannelId(localChannelId, remoteResponse.id)
                    dataStore.edit { preferences ->
                        preferences[intPreferencesKey("temp_ch_$tempChannelId")] = remoteResponse.id
                    }
                }
            }
            return@withContext Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.retry()
        }
    }
}