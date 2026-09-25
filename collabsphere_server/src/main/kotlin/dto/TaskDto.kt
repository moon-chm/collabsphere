package dto

import kotlinx.serialization.Serializable

@Serializable
data class TaskRequest(
    val taskName: String,
    val taskDescription: String,
    val assignedToUserId: Int?,
    val workspaceId: Int,
    val status: String,
    val idempotencyKey: String? = null,
    val dueDate: Long? = null,
    val priority: String? = null
)

@Serializable
data class TaskResponse(
    val id: Int,
    val createdByUserId: Int,
    val assignedToUserId: Int?,
    val workspaceId: Int,
    val taskName: String,
    val taskDescription: String,
    val status: String,
    val dueDate: Long? = null,
    val priority: String = "MEDIUM"
)

@Serializable
data class TaskSyncResponse(
    val id: Int,
    val createdByUserId: Int,
    val assignedToUserId: Int?,
    val workspaceId: Int,
    val taskName: String,
    val taskDescription: String,
    val status: String,
    val isDeleted: Boolean,
    val updatedAt: Long,
    val dueDate: Long? = null,
    val priority: String = "MEDIUM"
)

object TaskPriorities {
    val allowed = setOf("LOW", "MEDIUM", "HIGH")

    fun normalize(value: String?): String? =
        value?.trim()?.uppercase()?.takeIf { it in allowed }
}
