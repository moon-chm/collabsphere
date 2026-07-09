package com.example.rohit_project_challlange.model.channels

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.*
import com.example.rohit_project_challlange.dto.channel.ChannelRequest
import com.example.rohit_project_challlange.dto.channel.ChannelSyncDto
import com.example.rohit_project_challlange.remote.channel.ChannelApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ChannelRepo(
    private val channelDao: ChannelDao,
    private val apiService: ChannelApiService,
    private val workManager: WorkManager,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val LAST_SYNC_KEY = longPreferencesKey("channels_last_sync_time")
    }

    suspend fun addchanneltoscreen(
        workspaceId: Int,
        channelName: String,
        userId: Int?,
        description: String,
        fallbackEntity: ChannelEntity
    ): Result<Long> = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = ChannelRequest(
                id = 0,
                userId = userId,
                channelName = channelName,
                workspaceId = workspaceId,
                description = description
            )
            val remoteChannel = apiService.createChannel(request)
            val savedId = channelDao.createChannels(fallbackEntity.copy(id = remoteChannel.id))
            Result.success(savedId)
        } catch (e: Exception) {
            e.printStackTrace()
            val fallbackId = channelDao.createChannels(fallbackEntity)
            val syncData = workDataOf(
                "CHANNEL_ID" to fallbackId.toInt(),
                "WORKSPACE_ID" to workspaceId,
                "CHANNEL_NAME" to channelName,
                "USER_ID" to (userId ?: -1),
                "DESCRIPTION" to description
            )
            enqueueSync(syncData)
            Result.success(fallbackId)
        }
    }

    suspend fun deletechanneltoscreen(channelName: String, workspaceId: Int, userId: Int?): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val apiSuccess = apiService.deleteChannel(channelName, workspaceId, userId)
            val deletedRows = channelDao.deletechannel(channelName, workspaceId, userId ?: 0)
            deletedRows > 0 || apiSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            val deletedRows = channelDao.deletechannel(channelName, workspaceId, userId ?: 0)
            val syncData = workDataOf(
                "ACTION_TYPE" to "DELETE",
                "CHANNEL_NAME" to channelName,
                "WORKSPACE_ID" to workspaceId,
                "USER_ID" to (userId ?: 0)
            )
            enqueueSync(syncData)

            deletedRows > 0
        }
    }

    fun getallchannelbyuser(workspaceId: Int): Flow<List<ChannelEntity>> {
        return channelDao.getchannels(workspaceId)
            .onStart {
                try {
                    val remoteChannels = apiService.getchannelbyuser(workspaceId)
                    val channelEntities = remoteChannels.map { remote ->
                        ChannelEntity(
                            id = remote.id,
                            userId = remote.userId ?: 0,
                            workspaceId = remote.workspaceId,
                            channelName = remote.channelName,
                            description = remote.description
                        )
                    }
                    channelDao.insertAllChannels(channelEntities)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun startDeltaSyncLoop(workspaceId: Int) = withContext(Dispatchers.IO) {
        while (isActive) {
            try {
                val lastSyncTime = dataStore.data.map { it[LAST_SYNC_KEY] ?: 0L }.first()
                val updates = apiService.getChannelUpdates(workspaceId, lastSyncTime)

                if (updates.isNotEmpty()) {
                    updates.forEach { remote ->
                        if (remote.isDeleted) {
                            channelDao.deletechannel(
                                channelName = remote.channelName,
                                workspaceId = remote.workspaceId,
                                userId = remote.userId ?: 0
                            )
                        } else {
                            val entity = ChannelEntity(
                                id = remote.id,
                                userId = remote.userId ?: 0,
                                workspaceId = remote.workspaceId,
                                channelName = remote.channelName,
                                description = remote.description
                            )
                            channelDao.createChannels(entity)
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

        val syncRequest = OneTimeWorkRequestBuilder<ChannelSyncWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            "CHANNEL_SYNC_QUEUE",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }
}