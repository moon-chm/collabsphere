package com.example.rohit_project_challlange.model.dm

import androidx.work.*
import com.example.rohit_project_challlange.dto.dm.DmDto
import com.example.rohit_project_challlange.remote.dm.DmApiService
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class DmRepo(
    private val dmDao: DmDao,
    private val apiService: DmApiService,
    private val workManager: WorkManager
) {

    fun getDmHistory(workspaceId: Int, currentUserId: Int, chatPartnerId: Int): Flow<List<DmEntity>> {
        return dmDao.getDmHistory(workspaceId, currentUserId, chatPartnerId)
    }

    suspend fun connectToChat(baseUrl: String, currentUserId: Long) {
        apiService.connect(baseUrl, currentUserId)
    }

    suspend fun sendRealtimeDm(id: Int? = null, workspaceId: Int, senderId: Int, receiverId: Int, content: String) {
        val timestampVal = System.currentTimeMillis()
        val tempId = id ?: ((timestampVal and 0x7FFFFFFF).toInt().unaryMinus())

        val socketMessage = DmDto(
            action = "SEND_MESSAGE",
            workspaceId = workspaceId,
            senderId = senderId,
            receiverId = receiverId,
            content = content,
            timestamp = timestampVal,
            id = if (tempId < 0) null else tempId
        )

        val temporaryLocalEntity = DmEntity(
            id = tempId,
            workspaceId = workspaceId,
            senderId = senderId,
            receiverId = receiverId,
            dm_content = content,
            timestamp = socketMessage.timestamp
        )
        dmDao.sendDm(temporaryLocalEntity)

        try {
            apiService.sendDm(socketMessage)
        } catch (e: Exception) {
            e.printStackTrace()
            val syncData = workDataOf(
                "ACTION_TYPE" to "SEND_MESSAGE",
                "DM_ID" to tempId,
                "WORKSPACE_ID" to workspaceId,
                "SENDER_ID" to senderId,
                "RECEIVER_ID" to receiverId,
                "CONTENT" to content
            )
            enqueueSync(syncData)
        }
    }

    fun listenForIncomingDms(): Flow<DmDto> {
        return apiService.observeIncomingDms()
    }

    suspend fun saveIncomingDm(message: DmDto, currentUserId: Int) {
        if (message.action == "HISTORY") {
            val historyEntity = DmEntity(
                id = if (message.id == null || message.id == 0) message.hashCode() else message.id,
                workspaceId = message.workspaceId,
                senderId = message.senderId,
                receiverId = message.receiverId,
                dm_content = message.content,
                timestamp = message.timestamp
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
                    timestamp = message.timestamp
                )
                dmDao.deleteDmByContentAndTimestamp(message.content, message.timestamp)
                dmDao.sendDm(localEntity)
            }
            return
        }

        val localEntity = DmEntity(
            id = if (message.id == null || message.id == 0) message.hashCode() else message.id,
            workspaceId = message.workspaceId,
            senderId = message.senderId,
            receiverId = message.receiverId,
            dm_content = message.content,
            timestamp = message.timestamp
        )
        dmDao.sendDm(localEntity)
    }


    suspend fun deleteDm(dmId: Int, workspaceId: Int) {
        try {
            dmDao.deleteDm(dmId, workspaceId)
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
        } catch (e: Exception) {
            e.printStackTrace()
            val syncData = workDataOf(
                "ACTION_TYPE" to "DELETE_MESSAGE",
                "DM_ID" to dmId,
                "WORKSPACE_ID" to workspaceId
            )
            enqueueSync(syncData)
        }
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

        workManager.enqueueUniqueWork(
            "DM_SYNC_QUEUE_${System.currentTimeMillis()}",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }
}