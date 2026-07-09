package dto

import kotlinx.serialization.Serializable

@Serializable
data class ChannelRequest(
    val id: Int,
    val userId: Int?,
    val channelName: String,
    val workspaceId: Int,
    val description: String
)
@Serializable
data class ChannelResponse(
    val id: Int,
    val userId: Int?,
    val channelName: String,
    val workspaceId: Int,
    val description: String
)
@Serializable
data class ChannelSyncResponse(
    val id: Int,
    val userId: Int?,
    val channelName: String,
    val workspaceId: Int,
    val description: String,
    val isDeleted: Boolean,
    val updatedAt: Long
)