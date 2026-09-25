package com.collabsphere.dto

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
    val userId: Int?,
    val workspaceId: Int,
    val notesName: String,
    val description: String,
    val isPinned: Boolean = false
)

@Serializable
data class NotesSyncResponse(
    val id: Int,
    val userId: Int?,
    val workspaceId: Int,
    val notesName: String,
    val description: String,
    val isDeleted: Boolean,
    val updatedAt: Long,
    val isPinned: Boolean = false
)
@Serializable
data class FileSyncResponse(
    val id: Long,
    val userId: Int,
    val workspaceId: Int,
    val userName: String,
    val url: String,
    val mimeType: String,
    val localpath: String?,
    val fileName: String,
    val sizebytes: Long,
    val isDeleted: Boolean,
    val updatedAt: Long
)