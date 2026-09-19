package com.collabsphere.dto

import kotlinx.serialization.Serializable

// ── Own full profile (visible only to the authenticated user themselves) ──────

@Serializable
data class UserProfileResponse(
    val id: Int,
    val username: String,
    val email: String,
    val avatarUrl: String,          // never null — default SVG URL when no upload exists
    val bio: String?,
    val statusMessage: String?,
    val isEmailVerified: Boolean,
    val lastSeen: Long?,
    val showEmail: Boolean,
    val showOnlineStatus: Boolean,
    val showLastSeen: Boolean,
    val profileVisibility: String   // "public" | "members_only"
)

// ── Another user's profile — fields filtered by their own privacy settings ────

@Serializable
data class PublicProfileResponse(
    val id: Int,
    val username: String,
    val email: String?,             // null when target has showEmail = false
    val avatarUrl: String,
    val bio: String?,
    val statusMessage: String?,
    val isOnline: Boolean?,         // null when target has showOnlineStatus = false
    val lastSeen: Long?             // null when target has showLastSeen = false
)

// ── Privacy settings update ───────────────────────────────────────────────────

@Serializable
data class PrivacySettingsRequest(
    val showEmail: Boolean,
    val showOnlineStatus: Boolean,
    val showLastSeen: Boolean,
    val profileVisibility: String   // "public" | "members_only"
)

// ── Block / unblock ───────────────────────────────────────────────────────────

@Serializable
data class BlockUserResponse(
    val blockerId: Int,
    val blockedId: Int,
    val action: String              // "blocked" | "unblocked"
)

// ── User search ───────────────────────────────────────────────────────────────

@Serializable
data class SearchUserResult(
    val id: Int,
    val username: String,
    val email: String?,             // shown only when target allows it
    val avatarUrl: String
)

// ── Avatar upload ─────────────────────────────────────────────────────────────

@Serializable
data class AvatarUploadResponse(
    val avatarUrl: String
)

// ── Account deletion ──────────────────────────────────────────────────────────

@Serializable
data class DeleteAccountRequest(
    val password: String
)

// ── Email change ──────────────────────────────────────────────────────────────

@Serializable
data class ChangeEmailRequest(
    val newEmail: String,
    val currentPassword: String
)

// ── Email verification ────────────────────────────────────────────────────────

@Serializable
data class EmailVerifyConfirmRequest(
    val token: String
)
