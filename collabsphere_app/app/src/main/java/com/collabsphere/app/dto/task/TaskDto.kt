package com.collabsphere.app.dto.task

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
    val priority: String? = null,
    val checklist: List<ChecklistItem>? = null,
    val labels: List<String>? = null
)

@Serializable
data class ChecklistItem(
    val text: String,
    val done: Boolean = false
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
    val priority: String = "MEDIUM",
    val checklist: List<ChecklistItem> = emptyList(),
    val labels: List<String> = emptyList()
)

@Serializable
data class TaskSyncDto(
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
    val priority: String = "MEDIUM",
    val checklist: List<ChecklistItem> = emptyList(),
    val labels: List<String> = emptyList()
)
