package dto

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
    val query: String,
    val messages: List<SearchMessageHit> = emptyList(),
    val directMessages: List<SearchDmHit> = emptyList(),
    val tasks: List<SearchTaskHit> = emptyList(),
    val notes: List<SearchNoteHit> = emptyList(),
    val files: List<SearchFileHit> = emptyList()
)

object SearchText {
    const val MIN_QUERY_LENGTH = 2
    const val MAX_QUERY_LENGTH = 100

    fun snippet(text: String, query: String, radius: Int = 60): String {
        val index = text.indexOf(query, ignoreCase = true)
        if (index < 0) return text.take(radius * 2)
        val start = (index - radius).coerceAtLeast(0)
        val end = (index + query.length + radius).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end) + suffix
    }
}
