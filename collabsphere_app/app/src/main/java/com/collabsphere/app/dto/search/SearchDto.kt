package com.collabsphere.app.dto.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchMessageHit(
    val id: Int,
    val channelId: Int,
    val channelName: String,
    val userName: String,
    val content: String
)

@Serializable
data class SearchDmHit(
    val id: Int,
    val partnerId: Int,
    val partnerName: String,
    val content: String,
    val timestamp: Long
)

@Serializable
data class SearchTaskHit(
    val id: Int,
    val taskName: String,
    val status: String
)

@Serializable
data class SearchNoteHit(
    val id: Int,
    val notesName: String,
    val snippet: String
)

@Serializable
data class SearchFileHit(
    val id: Long,
    val fileName: String,
    val mimeType: String
)

@Serializable
data class WorkspaceSearchResponse(
    val query: String = "",
    val messages: List<SearchMessageHit> = emptyList(),
    val directMessages: List<SearchDmHit> = emptyList(),
    val tasks: List<SearchTaskHit> = emptyList(),
    val notes: List<SearchNoteHit> = emptyList(),
    val files: List<SearchFileHit> = emptyList()
) {
    val isEmpty: Boolean
        get() = messages.isEmpty() && directMessages.isEmpty() && tasks.isEmpty() && notes.isEmpty() && files.isEmpty()
}
