package com.example.rohit_project_challlange.model.task

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rohit_project_challlange.dto.task.TaskRequest
import com.example.rohit_project_challlange.remote.task.TaskApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TaskSyncWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val apiService: TaskApiService,
    private val taskDao: TaskDao,
    private val dataStore: DataStore<Preferences>
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val actionType = inputData.getString("ACTION_TYPE") ?: "CREATE"
        val taskId = inputData.getInt("TASK_ID", -1)

        if (taskId == -1) return@withContext Result.failure()

        try {
            if (actionType == "DELETE") {
                apiService.deleteTask(taskId)
                return@withContext Result.success()
            }

            val createdByUserId = inputData.getInt("CREATED_BY_USER_ID", -1)
            val assignedToUserId = inputData.getInt("ASSIGNED_TO_USER_ID", -1)
            val workspaceIdParam = inputData.getInt("WORKSPACE_ID", -1)
            val taskName = inputData.getString("TASK_NAME") ?: ""
            val taskDescription = inputData.getString("TASK_DESCRIPTION") ?: ""
            val status = inputData.getString("STATUS") ?: TaskStatus.TO_DO.name

            if (workspaceIdParam == -1) return@withContext Result.failure()

            val workspaceId = if (workspaceIdParam < 0) {
                val mappingKey = intPreferencesKey("temp_ws_$workspaceIdParam")
                dataStore.data.map { it[mappingKey] ?: workspaceIdParam }.first()
            } else {
                workspaceIdParam
            }

            val request = TaskRequest(
                taskName = taskName,
                taskDescription = taskDescription,
                assignedToUserId = if (assignedToUserId == -1) null else assignedToUserId,
                workspaceId = workspaceId,
                status = status
            )

            if (actionType == "UPDATE") {
                apiService.updateTask(taskId, request)
            } else {
                val remoteResponse = apiService.createTask(createdByUserId, request)
                if (taskId != remoteResponse.id) {
                    taskDao.updateTaskId(taskId, remoteResponse.id)
                }
            }
            return@withContext Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.retry()
        }
    }
}