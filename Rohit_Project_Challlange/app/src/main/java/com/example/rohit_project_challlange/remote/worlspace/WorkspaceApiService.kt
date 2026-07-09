package com.example.rohit_project_challlange.remote.worlspace

import com.example.rohit_project_challlange.dto.workspace.AddMemberRequest
import com.example.rohit_project_challlange.dto.workspace.MemberResponse
import com.example.rohit_project_challlange.dto.workspace.WorkspaceRequest
import com.example.rohit_project_challlange.dto.workspace.WorkspaceResponse
import com.example.rohit_project_challlange.dto.workspace.WorkspaceSyncDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.http.*

class WorkspaceApiService(
    private val client: HttpClient,
    private val baseUrl: String = "http://10.238.3.93:8080/api/workspace"
) {

    suspend fun createWorkspace(
        userId: Int,
        request: WorkspaceRequest
    ): WorkspaceResponse {
        return client.post("$baseUrl/create") {
            contentType(ContentType.Application.Json)
            parameter("userId", userId)
            setBody(request)
        }.body()
    }

    suspend fun addMemberToWorkspace(
        workspaceId: Int,
        request: AddMemberRequest
    ): MemberResponse {
        return client.post("$baseUrl/members/$workspaceId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteWorkspaceFromServer(
        workspaceName: String,
        userId: Int,
        workspacePassword: String
    ): Int {
        return client.delete("$baseUrl/delete") {
            parameter("workspaceName", workspaceName)
            parameter("userId", userId)
            parameter("workspacePassword", workspacePassword)
        }.body()
    }

    suspend fun getWorkspacesByUserId(userId: Int): List<WorkspaceResponse> {
        return client.get("$baseUrl/user/$userId") {
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun getWorkspaceUpdates(userId: Int, since: Long): List<WorkspaceSyncDto> {
        return client.get("$baseUrl/sync/$userId") {
            parameter("since", since)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }.body()
    }

    suspend fun getWorkspaceMembers(workspaceId: Int): List<MemberResponse> {
        return client.get("$baseUrl/members/$workspaceId") {
            contentType(ContentType.Application.Json)
        }.body()
    }
}