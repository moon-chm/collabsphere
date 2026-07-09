package com.example.rohit_project_challlange.model.task

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.*
import com.example.rohit_project_challlange.dto.task.TaskRequest
import com.example.rohit_project_challlange.model.UserEntity
import com.example.rohit_project_challlange.model.workspace.WorkspaceDao
import com.example.rohit_project_challlange.model.workspace.WorkspaceMemberEntity
import com.example.rohit_project_challlange.remote.task.TaskApiService
import com.example.rohit_project_challlange.remote.worlspace.WorkspaceApiService
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
        syncWorkspaceMembers(workspaceId)
        while (isActive) {
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
            delay(3000)
        }
    }

    suspend fun addTask(task: TaskEntity): Result<Long> = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = TaskRequest(
                taskName = task.taskName,
                taskDescription = task.taskDescription,
                assignedToUserId = task.assignedToUserId,
                workspaceId = task.workspaceId,
                status = task.status.name
            )
            val remoteTask = apiService.createTask(task.createdByUserId, request)
            val savedId = taskDao.insertTask(task.copy(id = remoteTask.id))
            Result.success(savedId)
        } catch (e: Exception) {
            Log.e("TaskRepo", "POST request failed.", e)
            val temporaryLocalId = System.currentTimeMillis().toInt() and Int.MAX_VALUE
            val fallbackId = taskDao.insertTask(task.copy(id = temporaryLocalId))
            val syncData = workDataOf(
                "ACTION_TYPE" to "CREATE",
                "TASK_ID" to temporaryLocalId,
                "CREATED_BY_USER_ID" to task.createdByUserId,
                "ASSIGNED_TO_USER_ID" to task.assignedToUserId,
                "WORKSPACE_ID" to task.workspaceId,
                "TASK_NAME" to task.taskName,
                "TASK_DESCRIPTION" to task.taskDescription,
                "STATUS" to task.status.name
            )
            enqueueSync(syncData)
            Result.success(fallbackId)
        }
    }

    suspend fun updateTask(task: TaskEntity): Result<Unit> = withContext(Dispatchers.IO) {
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
            Result.success(Unit)
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
            Result.success(Unit)
        }
    }

    suspend fun deleteTask(taskId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            apiService.deleteTask(taskId)
            taskDao.deleteTask(taskId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("TaskRepo", "DELETE request failed.", e)
            taskDao.deleteTask(taskId)
            val syncData = workDataOf(
                "ACTION_TYPE" to "DELETE",
                "TASK_ID" to taskId
            )
            enqueueSync(syncData)
            Result.success(Unit)
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

        workManager.enqueueUniqueWork(
            "TASK_SYNC_QUEUE",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }
}