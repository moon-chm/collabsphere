package com.collabsphere.app.model.task
import android.util.Log

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.collabsphere.app.dto.task.TaskRequest
import com.collabsphere.app.remote.task.TaskApiService
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
        val rawTaskId = inputData.getInt("TASK_ID", -1)

        if (rawTaskId == -1) return@withContext Result.failure()

        // An UPDATE/DELETE enqueued before this task's own CREATE resolved still carries the
        // frozen temp id in its inputData — resolve it via the same temp-id mapping the CREATE
        // branch below writes. If it hasn't resolved yet, retry later rather than 404ing forever.
        val taskId = if (rawTaskId < 0 && actionType != "CREATE") {
            val mappingKey = intPreferencesKey("temp_task_$rawTaskId")
            val resolved = dataStore.data.map { it[mappingKey] }.first()
            resolved ?: return@withContext Result.retry()
        } else {
            rawTaskId
        }

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
                status = status,
                idempotencyKey = inputData.getString("IDEMPOTENCY_KEY"),
                dueDate = if (inputData.keyValueMap.containsKey("DUE_DATE")) inputData.getLong("DUE_DATE", 0L) else null,
                priority = inputData.getString("PRIORITY")
            )

            if (actionType == "UPDATE") {
                apiService.updateTask(taskId, request)
            } else {
                val remoteResponse = apiService.createTask(createdByUserId, request)
                if (taskId != remoteResponse.id) {
                    taskDao.updateTaskId(taskId, remoteResponse.id)
                    dataStore.edit { it[intPreferencesKey("temp_task_$taskId")] = remoteResponse.id }
                }
            }
            return@withContext Result.success()

        } catch (e: Exception) {
            Log.e("TaskSyncWorker", "Operation failed", e)
            return@withContext Result.retry()
        }
    }
}