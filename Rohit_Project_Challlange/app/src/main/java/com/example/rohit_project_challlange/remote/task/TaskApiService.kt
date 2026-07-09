package com.example.rohit_project_challlange.remote.task

import com.example.rohit_project_challlange.dto.task.TaskRequest
import com.example.rohit_project_challlange.dto.task.TaskResponse
import com.example.rohit_project_challlange.dto.task.TaskSyncDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class TaskApiService(private val client: HttpClient) {

    private val baseUrl = "http://10.238.3.93:8080/api/tasks"

    suspend fun createTask(createdByUserId: Int, request: TaskRequest): TaskResponse {
        return client.post(baseUrl) {
            contentType(ContentType.Application.Json)
            parameter("userId", createdByUserId)
            setBody(request)
        }.body()
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
        }.body()
    }

    suspend fun deleteTask(taskId: Int): Boolean {
        val response = client.delete("$baseUrl/$taskId")
        return response.status.isSuccess()
    }

    suspend fun getTaskUpdates(workspaceId: Int, since: Long): List<TaskSyncDto> {
        return client.get("$baseUrl/sync/$workspaceId") {
            parameter("since", since)
            contentType(ContentType.Application.Json)
        }.body()
    }
}