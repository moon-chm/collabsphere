package dto

import kotlinx.serialization.Serializable

@Serializable
data class DmDto(
    val id: Int? = null,
    val action: String,
    val workspaceId: Int = 0,
    val senderId: Int = 0,
    val receiverId: Int = 0,
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    // Media sharing
    val mediaUrl: String? = null,
    // Reactions
    val emoji: String? = null,
    val reactions: Map<String, Int>? = null,
    val replyToId: Int? = null,
    val channelId: Int? = null,
    val isRead: Boolean = false,
    // Only ever true on rows from /api/dm/sync (a tombstone for a deleted DM). Left at its default
    // everywhere else, so it's omitted from socket frames and older clients never see the field.
    val isDeleted: Boolean = false
)