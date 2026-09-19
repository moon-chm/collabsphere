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
    val timestamp: Long = System.currentTimeMillis()
)