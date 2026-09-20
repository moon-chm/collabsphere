package com.example.rohit_project_challlange.dto.login

import kotlinx.serialization.Serializable

@Serializable
data class SearchUserResult(
    val id: Int,
    val username: String,
    val email: String? = null,
    val avatarUrl: String
)

@Serializable
data class PublicProfileResponse(
    val id: Int,
    val username: String,
    val email: String? = null,
    val avatarUrl: String,
    val bio: String? = null,
    val statusMessage: String? = null,
    val isOnline: Boolean? = null,
    val lastSeen: Long? = null
)

@Serializable
data class BlockUserResponse(
    val blockerId: Int,
    val blockedId: Int,
    val action: String
)

@Serializable
data class PrivacySettingsRequest(
    val showEmail: Boolean,
    val showOnlineStatus: Boolean,
    val showLastSeen: Boolean,
    val profileVisibility: String
)
