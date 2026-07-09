package com.example.rohit_project_challlange.dto.message

import kotlinx.serialization.Serializable

@Serializable
data class MessageRequest(
    val id: Int,
    val userId: Int,
    val workspaceId: Int,
    val channelId: Int,
    val userName: String,
    val content: String,
    val status: String
)

@Serializable
data class MessageResponse(
    val id: Int,
    val userId: Int,
    val workspaceId: Int,
    val channelId: Int,
    val userName: String,
    val content: String,
    val status: String
)

@Serializable
data class MessageSyncDto(
    val id: Int,
    val userId: Int,
    val workspaceId: Int,
    val channelId: Int,
    val userName: String,
    val content: String,
    val status: String,
    val isDeleted: Boolean,
    val updatedAt: Long
)