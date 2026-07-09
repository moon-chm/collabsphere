package com.example.rohit_project_challlange.dto.task

import kotlinx.serialization.Serializable

@Serializable
data class TaskRequest(
    val taskName: String,
    val taskDescription: String,
    val assignedToUserId: Int?,
    val workspaceId: Int,
    val status: String
)

@Serializable
data class TaskResponse(
    val id: Int,
    val createdByUserId: Int,
    val assignedToUserId: Int?,
    val workspaceId: Int,
    val taskName: String,
    val taskDescription: String,
    val status: String
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
    val updatedAt: Long
)