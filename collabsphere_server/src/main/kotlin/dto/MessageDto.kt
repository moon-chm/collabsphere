package com.collabsphere.dto

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
data class MessageSyncResponse(
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
    /** When the message was sent (falls back to its last update for messages older than this field). */
    val createdAt: Long? = null,
    val threadRootId: Int? = null
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
    val message: MessageSyncResponse
)

@Serializable
data class ChannelUnreadCount(
    val channelId: Int,
    val count: Int
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
    val workspaceName: String,
    val channelId: Int? = null,
    val channelName: String? = null,
    val partnerId: Int? = null,
    val partnerName: String? = null,
    val authorId: Int,
    val authorName: String,
    val content: String,
    val mediaUrl: String? = null,
    val sentAt: Long,
    val savedAt: Long
)
