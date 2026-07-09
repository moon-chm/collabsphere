package com.example.rohit_project_challlange.dto.notes

import kotlinx.serialization.Serializable

@Serializable
data class NotesRequest(
    val userId: Int,
    val workspaceId: Int,
    val notesName: String,
    val description: String
)

@Serializable
data class NotesResponse(
    val id: Int,
    val userId: Int,
    val workspaceId: Int,
    val notesName: String,
    val description: String
)

@Serializable
data class NotesSyncDto(
    val id: Int,
    val userId: Int,
    val workspaceId: Int,
    val notesName: String,
    val description: String,
    val isDeleted: Boolean,
    val updatedAt: Long
)