package com.collabsphere.dto

import kotlinx.serialization.Serializable

@Serializable
data class GitHubUnfurlRequest(
    val urls: List<String>
)

@Serializable
data class GitHubUnfurlResponse(
    val previews: Map<String, GitHubPreviewItem>
)

@Serializable
data class GitHubPreviewItem(
    val type: String, // "REPOSITORY", "PULL_REQUEST", "ISSUE", "COMMIT", "ERROR"
    val url: String,
    val title: String,
    val repoFullName: String,
    val description: String? = null,
    val number: Int? = null,
    val state: String? = null,
    val author: String? = null,
    val merged: Boolean? = null,
    val shortSha: String? = null,
    val timestamp: Long? = null,
    val isPrivate: Boolean? = null,
    val ciStatus: String? = null,
    val reason: String? = null
)
