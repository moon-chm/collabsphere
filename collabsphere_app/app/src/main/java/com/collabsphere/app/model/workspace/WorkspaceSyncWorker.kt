package com.collabsphere.app.model.workspace

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.collabsphere.app.dto.workspace.AddMemberRequest
import com.collabsphere.app.dto.workspace.WorkspaceRequest
import com.collabsphere.app.remote.workspace.WorkspaceApiService
import kotlinx.coroutines.Dispatchers
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
                    val workspaceName = inputData.getString("WORKSPACE_NAME") ?: return@withContext Result.failure()
                    val userId = inputData.getInt("USER_ID", -1)
                    val workspacePassword = inputData.getString("WORKSPACE_PASSWORD") ?: ""
                    if (userId == -1) return@withContext Result.failure()

                    apiService.deleteWorkspaceFromServer(workspaceName, userId, workspacePassword)
                }

                "ADD_MEMBER" -> {
                    val workspaceId = inputData.getInt("WORKSPACE_ID", -1)
                    val email = inputData.getString("EMAIL") ?: return@withContext Result.failure()
                    if (workspaceId == -1) return@withContext Result.failure()

                    apiService.addMemberToWorkspace(workspaceId, AddMemberRequest(email))
                }

                else -> {
                    val userId = inputData.getInt("USER_ID", -1)
                    val tempWorkspaceId = inputData.getInt("WORKSPACE_ID", -1)
                    val workspaceName = inputData.getString("WORKSPACE_NAME") ?: return@withContext Result.failure()
                    val workspaceOwner = inputData.getString("WORKSPACE_OWNER") ?: ""
                    val workspacePassword = inputData.getString("WORKSPACE_PASSWORD") ?: ""
                    if (userId == -1) return@withContext Result.failure()

                    val request = WorkspaceRequest(
                        workspaceName = workspaceName,
                        workspaceOwner = workspaceOwner,
                        workspacePassword = workspacePassword
                    )

                    val remoteResponse = apiService.createWorkspace(userId, request)
                    if (tempWorkspaceId < 0) {
                        repo.handleRemoteWorkspaceCreation(tempWorkspaceId, remoteResponse.id)
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}