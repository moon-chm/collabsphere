package com.example.rohit_project_challlange.model.dm
import android.util.Log

import androidx.work.*
import com.example.rohit_project_challlange.dto.dm.DmDto
import com.example.rohit_project_challlange.model.TempId
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
        val tempId = if (id == null || id == 0) {
            TempId.next()
        } else {
            id
        }

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
            Log.e("DmRepo", "Operation failed", e)
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
        if (message.action == "DELETE_MESSAGE") {
            val id = message.id
            if (id != null && id != 0) {
                dmDao.deleteDm(id, message.workspaceId)
                dmDao.deleteDmById(id)
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

        if (message.action == "HISTORY") {
            // A HISTORY payload always carries a real, server-assigned id. If it somehow doesn't,
            // hashCode() is not a stable/unique key — drop the message rather than risk silently
            // overwriting an unrelated row via a hash collision.
            val id = message.id
            if (id == null || id == 0) return
            val historyEntity = DmEntity(
                id = id,
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

        // Same reasoning as the HISTORY branch above: a real incoming message always has a
        // server-assigned id; without one there's nothing safe to key the local row on.
        val id = message.id
        if (id == null || id == 0) return
        val localEntity = DmEntity(
            id = id,
            workspaceId = message.workspaceId,
            senderId = message.senderId,
            receiverId = message.receiverId,
            dm_content = message.content,
            timestamp = message.timestamp
        )
        dmDao.sendDm(localEntity)
    }

    suspend fun updateDm(dmId: Int, workspaceId: Int, senderId: Int, receiverId: Int, newContent: String) {
        // Immediate local database update for 0ms latency in UI
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
        // Immediate local database deletion for 0ms latency in UI
        dmDao.deleteDm(dmId, workspaceId)
        dmDao.deleteDmById(dmId)

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

        // Unique per message, not a timestamp-suffixed name — that defeated enqueueUniqueWork's
        // dedup entirely and let unboundedly many parallel workers pile up under network outages.
        // Per-message keying gets the same failure-isolation as the other repos' sync queues without
        // that risk. See ChannelRepo.enqueueSync for why a name shared across every message is wrong too.
        val dmId = data.getInt("DM_ID", 0)
        workManager.enqueueUniqueWork(
            "DM_SYNC_$dmId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }
}