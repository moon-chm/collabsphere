package com.collabsphere.app.dto.login

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileResponse(
    val id: Int,
    val username: String,
    val email: String,
    val avatarUrl: String,
    val bio: String? = null,
    val statusMessage: String? = null,
    val isEmailVerified: Boolean = false,
    val lastSeen: Long? = null,
    val showEmail: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val showLastSeen: Boolean = true,
    val profileVisibility: String = "public"
)

@Serializable
data class AvatarUploadResponse(
    val avatarUrl: String
)

@Serializable
data class ChangeEmailRequest(
    val newEmail: String,
    val currentPassword: String
)

@Serializable
data class DeleteAccountRequest(
    val password: String
)

@Serializable
data class EmailVerifyConfirmRequest(
    val token: String
)
