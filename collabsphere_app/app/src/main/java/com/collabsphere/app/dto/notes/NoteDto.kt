package com.collabsphere.app.dto.notes

import kotlinx.serialization.Serializable

@Serializable
data class NotesRequest(
    val userId: Int,
    val workspaceId: Int,
    val notesName: String,
    val description: String,
    val idempotencyKey: String? = null
)

@Serializable
data class NotesResponse(
    val id: Int,
    val userId: Int,
    val workspaceId: Int,
    val notesName: String,
    val description: String,
    val isPinned: Boolean = false
)

@Serializable
data class NotesSyncDto(
    val id: Int,
    val userId: Int,
    val workspaceId: Int,
    val notesName: String,
    val description: String,
    val isDeleted: Boolean,
    val updatedAt: Long,
    val isPinned: Boolean = false
)