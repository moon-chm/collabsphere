package dto

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
    val workspacePassword: String? = ""
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
    val avatarUrl: String? = null,
    val role: String = "MEMBER"
)

@Serializable
data class RoleRequest(
    val role: String
)

object WorkspaceRoles {
    const val OWNER = "OWNER"
    const val ADMIN = "ADMIN"
    const val MEMBER = "MEMBER"

    fun canModerate(role: String?): Boolean = role == OWNER || role == ADMIN

    fun canRemove(actorRole: String, targetRole: String, isSelf: Boolean): Boolean = when {
        isSelf -> targetRole != OWNER
        targetRole == OWNER -> false
        targetRole == ADMIN -> actorRole == OWNER
        else -> canModerate(actorRole)
    }
}

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
    val createdAt: Long
)