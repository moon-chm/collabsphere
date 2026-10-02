package com.collabsphere.dto

import kotlinx.serialization.Serializable

@Serializable
enum class GitHubActionResultStatus {
    ActionSucceeded,
    ActionAlreadyApplied,
    AuthenticationRequired,
    PermissionDenied,
    ResourceNotFound,
    NotMergeable,
    HeadChanged,
    Conflict,
    RateLimited,
    ValidationFailed,
    GitHubUnavailable,
    UnknownFailure
}

@Serializable
data class GitHubActionRequest(
    val url: String,
    val action: String,
    val body: String? = null
)

@Serializable
data class GitHubActionResponse(
    val status: GitHubActionResultStatus,
    val message: String,
    val newState: String? = null
)
