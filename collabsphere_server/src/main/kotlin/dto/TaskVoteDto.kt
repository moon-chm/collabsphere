package dto

import kotlinx.serialization.Serializable

@Serializable
data class TaskVoteProposalRequest(
    val workspaceId: Int,
    val type: String, // "CREATE" or "DELETE"
    val taskName: String? = null,
    val taskDescription: String? = null,
    val assignedToUserId: Int? = null,
    val priority: String? = null,
    val dueDate: Long? = null,
    val checklist: List<ChecklistItem>? = null,
    val labels: List<String>? = null,
    val deleteReason: String? = null,
    val taskId: Int? = null,
    val idempotencyKey: String? = null,
    val deadlineHours: Int? = 48
)

@Serializable
data class CastVoteRequest(
    val vote: String // "APPROVE" or "REJECT"
)

@Serializable
data class TaskVoteProposalResponse(
    val id: Int,
    val workspaceId: Int,
    val proposerId: Int,
    val type: String,
    val status: String,
    val taskId: Int? = null,
    val taskName: String? = null,
    val taskDescription: String? = null,
    val assignedToUserId: Int? = null,
    val priority: String? = null,
    val dueDate: Long? = null,
    val checklist: List<ChecklistItem> = emptyList(),
    val labels: List<String> = emptyList(),
    val deleteReason: String? = null,
    val eligibleCount: Int,
    val approvalThreshold: Int,
    val approveCount: Int,
    val rejectCount: Int,
    val resolvedAt: Long? = null,
    val createdTaskId: Int? = null,
    val deadline: Long,
    val createdAt: Long,
    val myVote: String? = null
)

@Serializable
data class TaskVoteProposalSyncResponse(
    val id: Int,
    val workspaceId: Int,
    val proposerId: Int,
    val type: String,
    val status: String,
    val taskId: Int? = null,
    val taskName: String? = null,
    val taskDescription: String? = null,
    val assignedToUserId: Int? = null,
    val priority: String? = null,
    val dueDate: Long? = null,
    val checklist: List<ChecklistItem> = emptyList(),
    val labels: List<String> = emptyList(),
    val deleteReason: String? = null,
    val eligibleCount: Int,
    val approvalThreshold: Int,
    val approveCount: Int,
    val rejectCount: Int,
    val resolvedAt: Long? = null,
    val createdTaskId: Int? = null,
    val deadline: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val myVote: String? = null
)
