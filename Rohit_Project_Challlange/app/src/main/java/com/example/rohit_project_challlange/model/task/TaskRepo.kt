package com.example.rohit_project_challlange.model.task

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.example.rohit_project_challlange.model.TempId
import androidx.work.*
import com.example.rohit_project_challlange.dto.task.TaskRequest
import com.example.rohit_project_challlange.model.UserEntity
import com.example.rohit_project_challlange.model.workspace.WorkspaceDao
import com.example.rohit_project_challlange.model.workspace.WorkspaceMemberEntity
import com.example.rohit_project_challlange.remote.task.TaskApiService
import com.example.rohit_project_challlange.remote.workspace.WorkspaceApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class TaskRepo(
    private val taskDao: TaskDao,
    private val workspaceDao: WorkspaceDao,
    private val apiService: TaskApiService,
    private val workspaceApiService: WorkspaceApiService,
    private val workManager: WorkManager,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val LAST_SYNC_KEY_PREFIX = "tasks_last_sync_time_"
    }

    private fun getSyncKey(workspaceId: Int) = longPreferencesKey("${LAST_SYNC_KEY_PREFIX}$workspaceId")

    // TaskRepo is a Koin singleton shared by every TaskViewModel instance — without this guard,
    // navigating to the same workspace's tasks screen more than once (without popping the earlier
    // backstack entry) starts a second independent 3s poller against the same endpoint.
    private val activeSyncLoops = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    fun getTasks(workspaceId: Int): Flow<List<TaskEntity>> {
        return taskDao.getTasksForWorkspace(workspaceId)
    }

    suspend fun syncWorkspaceMembers(workspaceId: Int) = withContext(Dispatchers.IO) {
        try {
            val members = workspaceApiService.getWorkspaceMembers(workspaceId)
            members.forEach { member ->
                val verifiedName = if (member.userName.isNullOrBlank() || member.userName == "null") {
                    "User ${member.userId}"
                } else {
                    member.userName
                }

                workspaceDao.upsertUser(
                    UserEntity(
                        id = member.userId,
                        email = member.email,
                        password = "",
                        userName = verifiedName
                    )
                )

                workspaceDao.upsertMember(
                    WorkspaceMemberEntity(
                        workspaceId = workspaceId,
                        userId = member.userId
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("TaskRepo", "Explicit workspace member initialization synchronization failed", e)
        }
    }

    suspend fun syncTasks(workspaceId: Int) = withContext(Dispatchers.IO) {
        try {
            syncWorkspaceMembers(workspaceId)
            val remoteTasks = apiService.getTasksByWorkspace(workspaceId)

            val entities = remoteTasks.map { remote ->
                TaskEntity(
                    id = remote.id,
                    createdByUserId = remote.createdByUserId,
                    assignedToUserId = remote.assignedToUserId,
                    workspaceId = remote.workspaceId,
                    taskName = remote.taskName,
                    taskDescription = remote.taskDescription,
                    status = try { TaskStatus.valueOf(remote.status) } catch (e: Exception) { TaskStatus.TO_DO }
                )
            }
            entities.forEach { taskDao.insertTask(it) }
        } catch (e: Exception) {
            Log.e("TaskRepo", "Failed to sync tasks from remote source.", e)
        }
    }

    suspend fun startDeltaSyncLoop(workspaceId: Int) = withContext(Dispatchers.IO) {
        if (!activeSyncLoops.add(workspaceId)) return@withContext
        try {
        syncWorkspaceMembers(workspaceId)
        while (isActive) {
            com.example.rohit_project_challlange.MyApplication.isAppForegroundFlow.first { it }
            try {
                val syncKey = getSyncKey(workspaceId)
                val lastSyncTime = dataStore.data.map { it[syncKey] ?: 0L }.first()
                val updates = apiService.getTaskUpdates(workspaceId, lastSyncTime)

                if (updates.isNotEmpty()) {
                    updates.forEach { remote ->
                        if (remote.isDeleted) {
                            taskDao.deleteTask(remote.id)
                        } else {
                            val entity = TaskEntity(
                                id = remote.id,
                                createdByUserId = remote.createdByUserId,
                                assignedToUserId = remote.assignedToUserId,
                                workspaceId = remote.workspaceId,
                                taskName = remote.taskName,
                                taskDescription = remote.taskDescription,
                                status = try { TaskStatus.valueOf(remote.status) } catch (e: Exception) { TaskStatus.TO_DO }
                            )
                            taskDao.insertTask(entity)
                        }
                    }
                    val newestTimestamp = updates.maxOf { it.updatedAt }
                    dataStore.edit { preferences ->
                        preferences[syncKey] = newestTimestamp
                    }
                }
            } catch (e: Exception) {
                Log.e("TaskRepo", "Delta sync iteration error", e)
            }
            delay(1000)
        }
        } finally {
            activeSyncLoops.remove(workspaceId)
        }
    }

    suspend fun addTask(task: TaskEntity): Result<Long> = withContext(Dispatchers.IO) {
        // Generated once and reused on every retry of this same create (frozen into workDataOf
        // below) so a WorkManager retry after a successful-but-lost response is recognized
        // server-side as the same request instead of inserting a duplicate task.
        val idempotencyKey = java.util.UUID.randomUUID().toString()
        return@withContext try {
            val request = TaskRequest(
                taskName = task.taskName,
                taskDescription = task.taskDescription,
                assignedToUserId = task.assignedToUserId,
                workspaceId = task.workspaceId,
                status = task.status.name,
                idempotencyKey = idempotencyKey
            )
            val remoteTask = apiService.createTask(task.createdByUserId, request)
            val savedId = taskDao.insertTask(task.copy(id = remoteTask.id))
            Result.success(savedId)
        } catch (e: Exception) {
            Log.e("TaskRepo", "POST request failed.", e)
            // Negative range: a positive id here (even truncated from a timestamp) can collide with
            // a real server-assigned id synced down before this pending create's own sync resolves.
            val temporaryLocalId = TempId.next()
            val fallbackId = taskDao.insertTask(task.copy(id = temporaryLocalId))
            val syncData = workDataOf(
                "ACTION_TYPE" to "CREATE",
                "TASK_ID" to temporaryLocalId,
                "CREATED_BY_USER_ID" to task.createdByUserId,
                "ASSIGNED_TO_USER_ID" to task.assignedToUserId,
                "WORKSPACE_ID" to task.workspaceId,
                "TASK_NAME" to task.taskName,
                "TASK_DESCRIPTION" to task.taskDescription,
                "STATUS" to task.status.name,
                "IDEMPOTENCY_KEY" to idempotencyKey
            )
            enqueueSync(syncData)
            Result.success(fallbackId)
        }
    }

    suspend fun updateTask(task: TaskEntity): Result<TaskSyncOutcome> = withContext(Dispatchers.IO) {
        return@withContext try {
            taskDao.updateTask(task)
            val request = TaskRequest(
                taskName = task.taskName,
                taskDescription = task.taskDescription,
                assignedToUserId = task.assignedToUserId,
                workspaceId = task.workspaceId,
                status = task.status.name
            )
            apiService.updateTask(taskId = task.id, request = request)
            Result.success(TaskSyncOutcome.CONFIRMED)
        } catch (e: Exception) {
            Log.e("TaskRepo", "PUT request failed.", e)
            val syncData = workDataOf(
                "ACTION_TYPE" to "UPDATE",
                "TASK_ID" to task.id,
                "CREATED_BY_USER_ID" to task.createdByUserId,
                "ASSIGNED_TO_USER_ID" to task.assignedToUserId,
                "WORKSPACE_ID" to task.workspaceId,
                "TASK_NAME" to task.taskName,
                "TASK_DESCRIPTION" to task.taskDescription,
                "STATUS" to task.status.name
            )
            enqueueSync(syncData)
            Result.success(TaskSyncOutcome.QUEUED)
        }
    }

    suspend fun deleteTask(taskId: Int): Result<TaskSyncOutcome> = withContext(Dispatchers.IO) {
        return@withContext try {
            apiService.deleteTask(taskId)
            taskDao.deleteTask(taskId)
            Result.success(TaskSyncOutcome.CONFIRMED)
        } catch (e: Exception) {
            Log.e("TaskRepo", "DELETE request failed.", e)
            taskDao.deleteTask(taskId)
            val syncData = workDataOf(
                "ACTION_TYPE" to "DELETE",
                "TASK_ID" to taskId
            )
            enqueueSync(syncData)
            Result.success(TaskSyncOutcome.QUEUED)
        }
    }

    fun getWorkspaceMembers(workspaceId: Int): Flow<List<UserEntity>> {
        return taskDao.getWorkspaceMembers(workspaceId)
    }

    private fun enqueueSync(data: Data) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<TaskSyncWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        // Unique per task, not per entity TYPE — see ChannelRepo.enqueueSync for why a shared name
        // across every task would let one permanently-failed sync cancel every other task's queue.
        val taskId = data.getInt("TASK_ID", 0)
        workManager.enqueueUniqueWork(
            "TASK_SYNC_$taskId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }
}