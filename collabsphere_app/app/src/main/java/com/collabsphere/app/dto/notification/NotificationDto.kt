package com.collabsphere.app.dto.notification

import kotlinx.serialization.Serializable

@Serializable
data class NotificationResponse(
    val id: Int,
    val recipientId: Int,
    val actorId: Int? = null,
    val actorUsername: String? = null,
    val actorAvatarUrl: String? = null,
    val type: String,
    val title: String,
    val body: String,
    val workspaceId: Int? = null,
    val referenceId: Int? = null,
    val isRead: Boolean,
    val createdAt: Long
)

@Serializable
data class NotificationCountResponse(
    val total: Int,
    val unread: Int
)

@Serializable
data class MarkReadRequest(
    val ids: List<Int>? = null
)

@Serializable
data class NotificationPushFrame(
    val action: String = "NOTIFICATION",
    val notification: NotificationResponse
)

@Serializable
data class MuteSetting(
    val workspaceId: Int,
    val channelId: Int? = null
)

@Serializable
data class MuteRequest(
    val workspaceId: Int,
    val channelId: Int? = null,
    val muted: Boolean
)
