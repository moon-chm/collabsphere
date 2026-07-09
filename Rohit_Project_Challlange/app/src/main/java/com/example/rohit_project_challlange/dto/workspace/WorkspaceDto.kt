package com.example.rohit_project_challlange.dto.workspace

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
    val email: String
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