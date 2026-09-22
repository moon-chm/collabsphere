package com.collabsphere.app.dto.workspace

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceRequest(
    val workspaceName: String,
    val workspaceOwner: String,
    val workspacePassword: String
)

@Serializable
data class WorkspaceResponse(
    val id: Int,
    val userId: Int,
    val workspaceName: String,
    val workspaceOwner: String,
    val workspacePassword: String? = null
)

@Serializable
data class AddMemberRequest(
    val email: String
)

@Serializable
data class MemberResponse(
    val workspaceId: Int,
    val userId: Int,
    val userName: String,
    val email: String,
    val avatarUrl: String? = null
)
@Serializable
data class WorkspaceSyncDto(
    val id: Int,
    val userId: Int,
    val workspaceName: String,
    val workspaceOwner: String,
    val isDeleted: Boolean,
    val updatedAt: Long
)

@Serializable
data class DeleteWorkspaceResponse(
    val deletedIds: List<Int>
)

@Serializable
data class SendInvitationRequest(
    val email: String
)

@Serializable
data class JoinWorkspaceByCodeRequest(
    val inviteCode: String
)

@Serializable
data class InvitationResponse(
    val id: Int,
    val workspaceId: Int,
    val workspaceName: String,
    val inviterName: String,
    val inviteeEmail: String,
    val inviteCode: String,
    val status: String,
    val createdAt: Long,
    val expiresAt: Long
)