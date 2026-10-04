package com.collabsphere.app.remote.task

import com.collabsphere.app.remote.SyncPage
import com.collabsphere.app.remote.syncParameters
import com.collabsphere.app.remote.toSyncPage
import com.collabsphere.app.remote.requireSuccess
import com.collabsphere.app.AppConfig
import com.collabsphere.app.dto.task.TaskRequest
import com.collabsphere.app.dto.task.TaskResponse
import com.collabsphere.app.dto.task.TaskSyncDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class TaskApiService(private val client: HttpClient) {

    private val baseUrl: String get() = "${AppConfig.BASE_URL}/api/tasks"

    suspend fun createTask(createdByUserId: Int, request: TaskRequest): TaskResponse {
        return client.post(baseUrl) {
            contentType(ContentType.Application.Json)
            parameter("userId", createdByUserId)
            setBody(request)
        }.requireSuccess().body()
    }

    suspend fun getTasksByWorkspace(workspaceId: Int): List<TaskResponse> {
        return client.get("$baseUrl/workspace/$workspaceId") {
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun updateTask(taskId: Int, request: TaskRequest): TaskResponse {
        return client.put("$baseUrl/$taskId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccess().body()
    }

    suspend fun deleteTask(taskId: Int): HttpStatusCode =
        client.delete("$baseUrl/$taskId").status

    suspend fun getTaskUpdates(workspaceId: Int, since: Long, cursor: Long?): SyncPage<TaskSyncDto> {
        return client.get("$baseUrl/sync/$workspaceId") {
            syncParameters(since, cursor)
            contentType(ContentType.Application.Json)
        }.toSyncPage()
    }
}