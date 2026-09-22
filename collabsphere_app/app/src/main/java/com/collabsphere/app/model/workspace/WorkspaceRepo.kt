package com.collabsphere.app.model.workspace

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.*
import com.collabsphere.app.dto.workspace.AddMemberRequest
import com.collabsphere.app.dto.workspace.WorkspaceRequest
import com.collabsphere.app.model.TempId
import com.collabsphere.app.model.UserEntity
import com.collabsphere.app.remote.workspace.WorkspaceApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class WorkspaceRepo(
    private val workspaceDao: WorkspaceDao,
    private val workspaceApiService: WorkspaceApiService,
    private val workManager: WorkManager,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val LAST_SYNC_KEY = longPreferencesKey("workspaces_last_sync_time")
    }

    // WorkspaceRepo is a Koin singleton shared by every WorkspaceViewModel instance — without this
    // guard, repeated forward navigation (without popping the earlier backstack entry) starts a
    // second independent 3s poller against the same endpoint.
    private val activeSyncLoops = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    fun getAllWorkspacesForUser(userId: Int): Flow<List<WorkspaceEntity>> {
        return workspaceDao.getAllWorkspacesForUser(userId)
    }

    suspend fun startDeltaSyncLoop(userId: Int) = withContext(Dispatchers.IO) {
        if (!activeSyncLoops.add(userId)) return@withContext
        try {
        while (isActive) {
            // Suspends here entirely (no polling/wakeups) while backgrounded, resumes instantly
            // the moment the app returns to foreground — then goes straight to a fetch below.
            com.collabsphere.app.MyApplication.isAppForegroundFlow.first { it }
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
            delay(1000)
        }
        } finally {
            activeSyncLoops.remove(userId)
        }
    }

    suspend fun syncWorkspaces(userId: Int) = withContext(Dispatchers.IO) {
        try {
            // Only ever plant a placeholder row to satisfy the FK constraint on WorkspaceEntity.userId
            // when the real user row genuinely isn't cached yet — never overwrite it if it already is.
            if (workspaceDao.userCount(userId) == 0) {
                workspaceDao.upsertUser(
                    UserEntity(
                        id = userId,
                        email = "",
                        password = "",
                        userName = ""
                    )
                )
            }

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
                        userName = name,
                        avatarUrl = member.avatarUrl
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
                val tempId = TempId.next()
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
                UserEntity(
                    id = remote.userId,
                    email = remote.email,
                    password = "",
                    userName = name,
                    avatarUrl = remote.avatarUrl
                )
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
        // Delete locally by the exact workspace id(s) the server confirmed as deleted, never by name
        // alone — two distinct workspaces (the user's own and someone else's) can share a name.
        val deletedIds = workspaceApiService.deleteWorkspaceFromServer(workspaceName, userId, workspacePassword)
        deletedIds.forEach { workspaceDao.deleteWorkspaceById(it) }
        deletedIds.size
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

        // Unique per workspace (and per member email for ADD_MEMBER, since several distinct members
        // can be queued for the same workspace at once) — see ChannelRepo.enqueueSync for why a
        // shared name across every workspace would let one permanently-failed sync cancel the rest.
        val workspaceId = data.getInt("WORKSPACE_ID", 0)
        val email = data.getString("EMAIL")
        val uniqueKey = if (email != null) "WORKSPACE_SYNC_${workspaceId}_$email" else "WORKSPACE_SYNC_$workspaceId"

        workManager.enqueueUniqueWork(
            uniqueKey,
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