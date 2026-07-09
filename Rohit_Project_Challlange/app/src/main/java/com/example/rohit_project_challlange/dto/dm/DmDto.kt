package com.example.rohit_project_challlange.dto.dm

import kotlinx.serialization.Serializable

@Serializable
data class DmDto(
    val id: Int? = null,
    val action: String,
    val workspaceId: Int,
    val senderId: Int,
    val receiverId: Int,
    val content: String,
    val timestamp: Long
)