package com.collabsphere.app.model.workspace

import android.util.Log
import com.collabsphere.app.model.SyncPolicy
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.collabsphere.app.dto.workspace.AddMemberRequest
import com.collabsphere.app.dto.workspace.WorkspaceRequest
import com.collabsphere.app.remote.workspace.WorkspaceApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class WorkspaceSyncWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val apiService: WorkspaceApiService,
    private val repo: WorkspaceRepo
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val actionType = inputData.getString("ACTION_TYPE") ?: "CREATE"

        try {
            when (actionType) {
                "DELETE" -> {
                    val storedWorkspaceId = inputData.getInt("WORKSPACE_ID", 0)
                    val workspacePassword = inputData.getString("WORKSPACE_PASSWORD") ?: ""
                    if (storedWorkspaceId == 0) return@withContext Result.failure()
                    val workspaceId = resolveWorkspaceId(storedWorkspaceId)
                    if (workspaceId == 0 && storedWorkspaceId < 0) return@withContext Result.success()
                    if (workspaceId < 0) return@withContext Result.retry()
                    if (workspaceId == 0) return@withContext Result.failure()

                    apiService.deleteWorkspaceFromServer(workspaceId, workspacePassword)
                }

                "ADD_MEMBER" -> {
                    val storedWorkspaceId = inputData.getInt("WORKSPACE_ID", 0)
                    val email = inputData.getString("EMAIL") ?: return@withContext Result.failure()
                    if (storedWorkspaceId == 0) return@withContext Result.failure()
                    val workspaceId = resolveWorkspaceId(storedWorkspaceId)
                    if (workspaceId == 0) return@withContext Result.failure()
                    if (workspaceId < 0) return@withContext Result.retry()

                    apiService.addMemberToWorkspace(workspaceId, AddMemberRequest(email))
                }

                else -> {
                    val userId = inputData.getInt("USER_ID", -1)
                    val tempWorkspaceId = inputData.getInt("WORKSPACE_ID", 0)
                    val workspaceName = inputData.getString("WORKSPACE_NAME") ?: return@withContext Result.failure()
                    val workspaceOwner = inputData.getString("WORKSPACE_OWNER") ?: ""
                    val workspacePassword = inputData.getString("WORKSPACE_PASSWORD") ?: ""
                    val clientRequestId = inputData.getString("CLIENT_REQUEST_ID")
                    if (userId == -1 || tempWorkspaceId == 0) return@withContext Result.failure()

                    val request = WorkspaceRequest(
                        workspaceName = workspaceName,
                        workspaceOwner = workspaceOwner,
                        workspacePassword = workspacePassword,
                        clientRequestId = clientRequestId
                    )

                    val remoteResponse = apiService.createWorkspace(userId, request)
                    if (tempWorkspaceId < 0) {
                        repo.handleRemoteWorkspaceCreation(tempWorkspaceId, remoteResponse.id)
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("WorkspaceSyncWorker", "Operation failed", e)
            val decision = SyncPolicy.forFailure(e, isDelete = actionType == "DELETE")
            val tempWorkspaceId = inputData.getInt("WORKSPACE_ID", 0)
            if (actionType == "CREATE" && tempWorkspaceId < 0 && decision == com.collabsphere.app.model.SyncDecision.DROP) {
                try {
                    repo.markRemoteWorkspaceCreationFailed(tempWorkspaceId)
                } catch (mappingError: Exception) {
                    Log.e("WorkspaceSyncWorker", "Could not persist rejected workspace mapping", mappingError)
                    return@withContext Result.retry()
                }
            }
            SyncPolicy.toWorkResult(decision)
        }
    }

    private suspend fun resolveWorkspaceId(storedWorkspaceId: Int): Int =
        if (storedWorkspaceId > 0) storedWorkspaceId
        else repo.observeCanonicalWorkspaceId(storedWorkspaceId).first()
}
