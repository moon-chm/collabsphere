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
    val pinnedAt: Long? = null
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
    val pinnedAt: Long? = null
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
    val pinnedAt: Long? = null
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