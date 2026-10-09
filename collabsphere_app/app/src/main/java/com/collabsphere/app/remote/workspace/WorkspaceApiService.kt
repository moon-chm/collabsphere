package com.collabsphere.app.remote.workspace

import com.collabsphere.app.remote.SyncPage
import com.collabsphere.app.remote.syncParameters
import com.collabsphere.app.remote.toSyncPage
import com.collabsphere.app.remote.requireSuccess
import com.collabsphere.app.AppConfig
import com.collabsphere.app.dto.search.WorkspaceSearchResponse
import com.collabsphere.app.dto.workspace.AddMemberRequest
import com.collabsphere.app.dto.workspace.MemberResponse
import com.collabsphere.app.dto.workspace.RoleRequest
import com.collabsphere.app.dto.workspace.WorkspaceRequest
import com.collabsphere.app.dto.workspace.WorkspaceResponse
import com.collabsphere.app.dto.workspace.WorkspaceSyncDto
import com.collabsphere.app.dto.workspace.DeleteWorkspaceResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class WorkspaceApiService(
    private val client: HttpClient,
    private val baseUrl: String = "${AppConfig.BASE_URL}/api/workspace"
) {

    suspend fun createWorkspace(
        userId: Int,
        request: WorkspaceRequest
    ): WorkspaceResponse {
        return client.post("$baseUrl/create") {
            contentType(ContentType.Application.Json)
            parameter("userId", userId)
            setBody(request)
        }.requireSuccess().body()
    }

    suspend fun searchWorkspace(workspaceId: Int, query: String): WorkspaceSearchResponse {
        val response = client.get("$baseUrl/$workspaceId/search") {
            parameter("q", query)
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Search failed (${response.status.value})")
        }
        return response.body()
    }

    suspend fun addMemberToWorkspace(
        workspaceId: Int,
        request: AddMemberRequest
    ): MemberResponse {
        return client.post("$baseUrl/members/$workspaceId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccess().body()
    }

    suspend fun deleteWorkspaceFromServer(
        workspaceId: Int,
        workspacePassword: String
    ): List<Int> {
        return client.delete("$baseUrl/delete") {
            parameter("workspaceId", workspaceId)
            header("X-Workspace-Password", workspacePassword)
        }.requireSuccess().body<DeleteWorkspaceResponse>().deletedIds
    }

    suspend fun getWorkspacesByUserId(userId: Int): List<WorkspaceResponse> {
        return client.get("$baseUrl/user/$userId") {
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun getWorkspaceUpdates(userId: Int, since: Long, cursor: Long?): SyncPage<WorkspaceSyncDto> {
        return client.get("$baseUrl/sync/$userId") {
            syncParameters(since, cursor)
        }.toSyncPage()
    }

    suspend fun removeMember(workspaceId: Int, userId: Int): HttpStatusCode =
        client.delete("$baseUrl/$workspaceId/members/$userId").status

    suspend fun setMemberRole(workspaceId: Int, userId: Int, role: String): HttpStatusCode =
        client.put("$baseUrl/$workspaceId/members/$userId/role") {
            contentType(ContentType.Application.Json)
            setBody(RoleRequest(role))
        }.status

    suspend fun getWorkspaceMembers(workspaceId: Int): List<MemberResponse> {
        return client.get("$baseUrl/members/$workspaceId") {
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun sendInvitation(
        workspaceId: Int,
        request: com.collabsphere.app.dto.workspace.SendInvitationRequest
    ): com.collabsphere.app.dto.workspace.InvitationResponse {
        return client.post("$baseUrl/invitations/$workspaceId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun getPendingInvitations(): List<com.collabsphere.app.dto.workspace.InvitationResponse> {
        return client.get("$baseUrl/invitations/pending") {
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun acceptInvitation(invitationId: Int): MemberResponse {
        return client.post("$baseUrl/invitations/$invitationId/accept") {
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun declineInvitation(invitationId: Int): Boolean {
        val response = client.post("$baseUrl/invitations/$invitationId/decline") {
            contentType(ContentType.Application.Json)
        }
        return response.status.isSuccess()
    }

    suspend fun joinByCode(request: com.collabsphere.app.dto.workspace.JoinWorkspaceByCodeRequest): MemberResponse {
        return client.post("$baseUrl/join-by-code") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}
