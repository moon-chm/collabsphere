package com.collabsphere.app.model.vote

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.collabsphere.app.dto.vote.TaskVoteProposalRequest
import com.collabsphere.app.dto.vote.TaskVoteProposalResponse
import com.collabsphere.app.dto.vote.TaskVoteProposalSyncResponse
import com.collabsphere.app.remote.vote.TaskVoteApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

class TaskVoteRepo(
    private val taskVoteDao: TaskVoteDao,
    private val apiService: TaskVoteApiService,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private const val TAG = "TaskVoteRepo"
        private const val LAST_SYNC_KEY_PREFIX = "task_votes_last_sync_time_"
    }

    private fun getSyncKey(workspaceId: Int) = longPreferencesKey("${LAST_SYNC_KEY_PREFIX}$workspaceId")
    private val activeSyncLoops = ConcurrentHashMap.newKeySet<Int>()

    fun getPendingProposals(workspaceId: Int): Flow<List<TaskVoteProposalEntity>> =
        taskVoteDao.getPendingProposals(workspaceId)

    fun getProposalHistory(workspaceId: Int): Flow<List<TaskVoteProposalEntity>> =
        taskVoteDao.getProposalHistory(workspaceId)

    fun getAllProposals(workspaceId: Int): Flow<List<TaskVoteProposalEntity>> =
        taskVoteDao.getAllProposals(workspaceId)

    fun getProposal(id: Int): Flow<TaskVoteProposalEntity?> =
        taskVoteDao.getProposalById(id)

    suspend fun submitProposal(request: TaskVoteProposalRequest): Result<TaskVoteProposalEntity> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.submitProposal(request)
                val entity = response.toEntity()
                taskVoteDao.insertProposal(entity)
                Result.success(entity)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Failed to submit proposal", e)
                Result.failure(e)
            }
        }

    suspend fun castVote(proposalId: Int, vote: String): Result<TaskVoteProposalEntity> =
        withContext(Dispatchers.IO) {
            try {
                // Optimistic local update
                taskVoteDao.updateMyVote(proposalId, vote)

                val response = apiService.castVote(proposalId, vote)
                val entity = response.toEntity()
                taskVoteDao.insertProposal(entity)
                Result.success(entity)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Failed to cast vote on proposal $proposalId", e)
                Result.failure(e)
            }
        }

    suspend fun fetchProposals(workspaceId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val proposals = apiService.getProposalsByWorkspace(workspaceId)
                val entities = proposals.map { it.toEntity() }
                taskVoteDao.insertProposals(entities)
                Result.success(Unit)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Failed to fetch proposals for workspace $workspaceId", e)
                Result.failure(e)
            }
        }

    suspend fun startDeltaSyncLoop(workspaceId: Int) = withContext(Dispatchers.IO) {
        if (!activeSyncLoops.add(workspaceId)) {
            Log.d(TAG, "Task vote delta sync loop already running for workspace $workspaceId — skipping")
            return@withContext
        }
        try {
            while (coroutineContext.isActive) {
                try {
                    val lastSync = dataStore.data.first()[getSyncKey(workspaceId)] ?: 0L
                    val syncUpdates = apiService.syncProposals(workspaceId, lastSync)
                    if (syncUpdates.isNotEmpty()) {
                        val entities = syncUpdates.map { it.toEntity() }
                        taskVoteDao.insertProposals(entities)
                        val maxUpdatedAt = syncUpdates.maxOf { it.updatedAt }
                        dataStore.edit { prefs ->
                            prefs[getSyncKey(workspaceId)] = maxUpdatedAt
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "Task vote delta sync failed for workspace $workspaceId: ${e.message}")
                }
                delay(4000)
            }
        } finally {
            activeSyncLoops.remove(workspaceId)
        }
    }

    private fun TaskVoteProposalResponse.toEntity() = TaskVoteProposalEntity(
        id = id,
        workspaceId = workspaceId,
        proposerId = proposerId,
        type = type,
        status = status,
        taskId = taskId,
        taskName = taskName,
        taskDescription = taskDescription,
        assignedToUserId = assignedToUserId,
        priority = priority,
        dueDate = dueDate,
        checklist = checklist,
        labels = labels,
        deleteReason = deleteReason,
        eligibleCount = eligibleCount,
        approvalThreshold = approvalThreshold,
        approveCount = approveCount,
        rejectCount = rejectCount,
        resolvedAt = resolvedAt,
        createdTaskId = createdTaskId,
        deadline = deadline,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
        myVote = myVote
    )

    private fun TaskVoteProposalSyncResponse.toEntity() = TaskVoteProposalEntity(
        id = id,
        workspaceId = workspaceId,
        proposerId = proposerId,
        type = type,
        status = status,
        taskId = taskId,
        taskName = taskName,
        taskDescription = taskDescription,
        assignedToUserId = assignedToUserId,
        priority = priority,
        dueDate = dueDate,
        checklist = checklist,
        labels = labels,
        deleteReason = deleteReason,
        eligibleCount = eligibleCount,
        approvalThreshold = approvalThreshold,
        approveCount = approveCount,
        rejectCount = rejectCount,
        resolvedAt = resolvedAt,
        createdTaskId = createdTaskId,
        deadline = deadline,
        createdAt = createdAt,
        updatedAt = updatedAt,
        myVote = myVote
    )
}
