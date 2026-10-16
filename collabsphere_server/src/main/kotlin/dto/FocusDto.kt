package com.collabsphere.dto

import kotlinx.serialization.Serializable

@Serializable
data class SetFocusRequest(val until: Long? = null)

@Serializable
data class FocusStatus(val userId: Int, val focusUntil: Long? = null)

@Serializable
data class HoldMessageRequest(
    val workspaceId: Int,
    val receiverId: Int,
    val content: String
)

@Serializable
data class UrgentMessageResult(val messageId: Int, val remainingToday: Int)

