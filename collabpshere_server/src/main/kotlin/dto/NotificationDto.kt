package com.collabsphere.dto

import kotlinx.serialization.Serializable

// ── REST response: full notification object ───────────────────────────────────

@Serializable
data class NotificationResponse(
    val id: Int,
    val recipientId: Int,
    val actorId: Int?,
    val actorUsername: String?,       // resolved from actors table join
    val actorAvatarUrl: String?,      // resolved via AvatarGenerator
    val type: String,                 // DM | CHANNEL_MESSAGE | MENTION | TASK_ASSIGNED | TASK_UPDATED
    val title: String,
    val body: String,
    val workspaceId: Int?,
    val referenceId: Int?,
    val isRead: Boolean,
    val createdAt: Long
)

// ── Badge count: total + unread ───────────────────────────────────────────────

@Serializable
data class NotificationCountResponse(
    val total: Int,
    val unread: Int
)

// ── Mark-read request: null ids = mark ALL read ───────────────────────────────

@Serializable
data class MarkReadRequest(
    val ids: List<Int>? = null        // null → mark all; list → mark specific ids
)

// ── WebSocket real-time push frame ────────────────────────────────────────────

@Serializable
data class NotificationPushFrame(
    val action: String = "NOTIFICATION",
    val notification: NotificationResponse
)
