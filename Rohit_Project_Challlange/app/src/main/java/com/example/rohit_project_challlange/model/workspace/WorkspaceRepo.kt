package com.example.rohit_project_challlange.model.workspace

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.*
import com.example.rohit_project_challlange.dto.workspace.AddMemberRequest
import com.example.rohit_project_challlange.dto.workspace.WorkspaceRequest
import com.example.rohit_project_challlange.model.UserEntity
import com.example.rohit_project_challlange.remote.worlspace.WorkspaceApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class WorkspaceRepo(
    private val workspaceDao: WorkspaceDao,
    private val workspaceApiService: WorkspaceApiService,
    private val workManager: WorkManager,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val LAST_SYNC_KEY = longPreferencesKey("workspaces_last_sync_time")
    }

    fun getAllWorkspacesForUser(userId: Int): Flow<List<WorkspaceEntity>> {
        return workspaceDao.getAllWorkspacesForUser(userId)
    }

    suspend fun startDeltaSyncLoop(userId: Int) = withContext(Dispatchers.IO) {
        while (isActive) {
            try {
                val lastSyncTime = dataStore.data.map { it[LAST_SYNC_KEY] ?: 0L }.first()
                val updates = workspaceApiService.getWorkspaceUpdates(userId, lastSyncTime)

                if (updates.isNotEmpty()) {
                    updates.forEach { remote ->
                        if (remote.isDeleted) {
                            workspaceDao.deleteWorkspaceById(remote.id)
                        } else {
                            val entity = WorkspaceEntity(
                                id = remote.id,
                                userId = remote.userId,
                                workspaceName = remote.workspaceName,
                                workspaceOwner = remote.workspaceOwner,
                                workspacePassword = ""
                            )
                            workspaceDao.upsertWorkspace(entity)
                            syncWorkspaceMembers(remote.id)
                        }
                    }

                    val newestTimestamp = updates.maxOf { it.updatedAt }
                    dataStore.edit { preferences ->
                        preferences[LAST_SYNC_KEY] = newestTimestamp
                    }
                }
            } catch (e: Exception) {
                Log.e("WorkspaceRepo", "Delta sync iteration error", e)
            }
            delay(3000)
        }
    }

    suspend fun syncWorkspaces(userId: Int) = withContext(Dispatchers.IO) {
        try {
            workspaceDao.upsertUser(
                UserEntity(
                    id = userId,
                    email = "currentuser@collabsphere.com",
                    password = "",
                    userName = "Logged In User"
                )
            )

            val remote = workspaceApiService.getWorkspacesByUserId(userId)
            val workspaceEntities = remote.map {
                WorkspaceEntity(
                    id = it.id,
                    userId = it.userId,
                    workspaceName = it.workspaceName,
                    workspaceOwner = it.workspaceOwner,
                    workspacePassword = ""
                )
            }

            workspaceEntities.forEach { workspace ->
                workspaceDao.upsertWorkspace(workspace)
                syncWorkspaceMembers(workspace.id)
            }
        } catch (e: Exception) {
            Log.e("WorkspaceRepo", "Fallback baseline tracking failed", e)
        }
    }

    fun getWorkspaceMembersFlow(workspaceId: Int): Flow<List<UserEntity>> {
        return workspaceDao.getWorkspaceMembers(workspaceId)
    }

    suspend fun syncWorkspaceMembers(workspaceId: Int) = withContext(Dispatchers.IO) {
        try {
            if (workspaceId < 0) return@withContext
            val members = workspaceApiService.getWorkspaceMembers(workspaceId)
            members.forEach { member ->
                val name = if (member.userName.isNullOrBlank() || member.userName == "null") {
                    "User ${member.userId}"
                } else member.userName

                workspaceDao.upsertUser(
                    UserEntity(
                        id = member.userId,
                        email = member.email,
                        password = "",
                        userName = name
                    )
                )

                workspaceDao.upsertMember(
                    WorkspaceMemberEntity(workspaceId = workspaceId, userId = member.userId)
                )
            }
        } catch (e: Exception) {
            Log.e("WorkspaceRepo", "Workspace member sync failed", e)
        }
    }

    suspend fun addWorkspaceToScreen(workspace: WorkspaceEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val request = WorkspaceRequest(
                    workspaceName = workspace.workspaceName,
                    workspaceOwner = workspace.workspaceOwner,
                    workspacePassword = workspace.workspacePassword
                )

                val remote = workspaceApiService.createWorkspace(workspace.userId, request)
                val newWorkspace = workspace.copy(id = remote.id)

                workspaceDao.upsertWorkspace(newWorkspace)
                workspaceDao.upsertMember(
                    WorkspaceMemberEntity(workspaceId = remote.id, userId = workspace.userId)
                )
                Result.success(Unit)
            } catch (e: Exception) {
                val tempId = Random.nextInt(-999999, -1)
                val temporaryEntity = workspace.copy(id = tempId)

                workspaceDao.upsertWorkspace(temporaryEntity)
                workspaceDao.upsertMember(
                    WorkspaceMemberEntity(workspaceId = tempId, userId = workspace.userId)
                )

                enqueueSync(
                    workDataOf(
                        "ACTION_TYPE" to "CREATE",
                        "WORKSPACE_ID" to tempId,
                        "USER_ID" to workspace.userId,
                        "WORKSPACE_NAME" to workspace.workspaceName,
                        "WORKSPACE_OWNER" to workspace.workspaceOwner,
                        "WORKSPACE_PASSWORD" to workspace.workspacePassword
                    )
                )
                Result.success(Unit)
            }
        }

    suspend fun addMemberToWorkspaceByEmail(
        workspaceId: Int,
        email: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trimmed = email.trim().lowercase()
            val remote = workspaceApiService.addMemberToWorkspace(workspaceId, AddMemberRequest(trimmed))

            val name = if (remote.userName.isNullOrBlank() || remote.userName == "null") {
                "User ${remote.userId}"
            } else remote.userName

            workspaceDao.upsertUser(
                UserEntity(id = remote.userId, email = remote.email, password = "", userName = name)
            )

            workspaceDao.upsertMember(
                WorkspaceMemberEntity(workspaceId = workspaceId, userId = remote.userId)
            )
            Result.success(Unit)
        } catch (e: Exception) {
            val userId = workspaceDao.getUserIdByEmail(email.trim().lowercase())
                ?: return@withContext Result.failure(Exception("User not found"))

            workspaceDao.upsertMember(
                WorkspaceMemberEntity(workspaceId = workspaceId, userId = userId)
            )

            enqueueSync(
                workDataOf(
                    "ACTION_TYPE" to "ADD_MEMBER",
                    "WORKSPACE_ID" to workspaceId,
                    "EMAIL" to email.trim().lowercase()
                )
            )
            Result.success(Unit)
        }
    }

    suspend fun deleteWorkspaceFromScreen(
        workspaceName: String,
        userId: Int,
        workspacePassword: String
    ): Int = withContext(Dispatchers.IO) {
        try {
            val serverRowsAffected = workspaceApiService.deleteWorkspaceFromServer(workspaceName, userId, workspacePassword)

            if (serverRowsAffected > 0) {
                return@withContext workspaceDao.deleteWorkspaceFromScreen(workspaceName, userId, workspacePassword)
            } else {
                return@withContext 0
            }
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun isUserMember(workspaceId: Int, email: String): Boolean = withContext(Dispatchers.IO) {
        val userId = workspaceDao.getUserIdByEmail(email.trim().lowercase()) ?: return@withContext false
        workspaceDao.isUserMember(workspaceId, userId) > 0
    }

    private fun enqueueSync(data: Data) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<WorkspaceSyncWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            "WORKSPACE_SYNC_QUEUE",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    suspend fun handleRemoteWorkspaceCreation(tempId: Int, realId: Int) {
        workspaceDao.swapWorkspaceId(tempId, realId)
        dataStore.edit { preferences ->
            preferences[androidx.datastore.preferences.core.intPreferencesKey("temp_ws_$tempId")] = realId
        }
    }
}