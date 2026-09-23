package com.collabsphere.dto

import kotlinx.serialization.Serializable

@Serializable
data class GitHubAnalyticsResponse(
    val isConnected: Boolean,
    val hasConnection: Boolean = false,
    val repositoryName: String?,
    val totalCommits: Int,
    val openPullRequests: Int,
    val mergedPullRequests: Int,
    val topContributors: List<GitHubContributorStats>
)

@Serializable
data class GitHubContributorStats(
    val username: String,
    val commits: Int
)
