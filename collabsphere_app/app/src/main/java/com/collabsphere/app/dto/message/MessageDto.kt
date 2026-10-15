package com.collabsphere.app.dto.message

import kotlinx.serialization.Serializable

@Serializable
data class MessageRequest(
    val id: Int,
    val userId: Int,
    val workspaceId: Int,
    val channelId: Int,
    val userName: String,
    val content: String,
    val status: String,
    val replyToId: Int? = null,
    val mediaUrl: String? = null,
    val pinnedAt: Long? = null,
    val idempotencyKey: String? = null,
    val threadRootId: Int? = null
)

@Serializable
data class MessageResponse(
    val id: Int,
    val userId: Int,
    val workspaceId: Int,
    val channelId: Int,
    val userName: String,
    val content: String,
    val status: String,
    val replyToId: Int? = null,
    val mediaUrl: String? = null,
    val pinnedAt: Long? = null,
    val editedAt: Long? = null,
    val createdAt: Long? = null,
    val threadRootId: Int? = null
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
    val updatedAt: Long,
    val replyToId: Int? = null,
    val mediaUrl: String? = null,
    val pinnedAt: Long? = null,
    val editedAt: Long? = null,
    /** When the message was sent (older messages report their last update instead). */
    val createdAt: Long? = null,
    val threadRootId: Int? = null
)

/** GET /api/message/unread/{workspaceId}: unread messages by others, per channel (zero counts omitted). */
@Serializable
data class ChannelUnreadCount(
    val channelId: Int,
    val count: Int
)

@Serializable
data class ChannelReadRequest(
    val lastReadMessageId: Int
)

@Serializable
data class ChannelReadState(
    val action: String = "CHANNEL_READ",
    val workspaceId: Int,
    val channelId: Int,
    val userId: Int,
    val userName: String,
    val lastReadMessageId: Int
)

@Serializable
data class LinkPreview(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null
)

@Serializable
data class PinRequest(
    val pinned: Boolean
)

@Serializable
data class ChannelReactionRequest(
    val emoji: String,
    val add: Boolean
)

@Serializable
data class ChannelReactionSummary(
    val action: String = "CHANNEL_REACTION",
    val messageId: Int,
    val channelId: Int,
    val workspaceId: Int,
    val reactors: Map<String, List<Int>>
)

@Serializable
data class ChannelTypingEvent(
    val action: String = "CHANNEL_TYPING",
    val workspaceId: Int,
    val channelId: Int,
    val userId: Int,
    val userName: String,
    val isTyping: Boolean
)

@Serializable
data class ChannelMessageEvent(
    val action: String = "CHANNEL_MESSAGE_EVENT",
    val message: MessageSyncDto
)

@Serializable
data class SaveMessageRequest(
    val kind: String,
    val messageId: Int
)

@Serializable
data class SavedMessageItem(
    val kind: String,
    val messageId: Int,
    val workspaceId: Int,
    val workspaceName: String = "",
    val channelId: Int? = null,
    val channelName: String? = null,
    val partnerId: Int? = null,
    val partnerName: String? = null,
    val authorId: Int,
    val authorName: String = "",
    val content: String = "",
    val mediaUrl: String? = null,
    val sentAt: Long,
    val savedAt: Long
)

@Serializable
data class CreatePollRequest(
    val question: String,
    val options: List<String>,
    val allowsMultiple: Boolean = false
)

@Serializable
data class PollVoteRequest(val optionIndexes: List<Int>)

@Serializable
data class PollOptionView(val text: String, val votes: Int = 0)

@Serializable
data class PollView(
    val messageId: Int,
    val question: String,
    val options: List<PollOptionView>,
    val allowsMultiple: Boolean = false,
    val myVotes: List<Int> = emptyList(),
    val totalVoters: Int = 0,
    val closed: Boolean = false
)

@Serializable
data class ScheduleMessageRequest(
    val kind: String,
    val workspaceId: Int,
    val channelId: Int? = null,
    val receiverId: Int? = null,
    val content: String,
    val sendAt: Long
)

@Serializable
data class ScheduledMessageItem(
    val id: Int,
    val kind: String,
    val workspaceId: Int,
    val channelId: Int? = null,
    val receiverId: Int? = null,
    val content: String,
    val sendAt: Long,
    val status: String = "PENDING"
)
