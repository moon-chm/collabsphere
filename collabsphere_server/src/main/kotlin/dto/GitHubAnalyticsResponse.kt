package com.collabsphere.dto

import kotlinx.serialization.Serializable

@Serializable
data class GitHubAnalyticsResponse(
    val isConnected: Boolean,
    val hasConnection: Boolean = false,
    val canManage: Boolean = false,
    val repositoryName: String?,
    val repositoryUrl: String? = null,
    val lastSyncedAt: Long? = null,
    val isSyncing: Boolean = false,
    val totalCommits: Int,
    val openPullRequests: Int,
    val mergedPullRequests: Int,
    val topContributors: List<GitHubContributorStats>,
    val commitActivity: List<GitHubDailyCount> = emptyList(),
    val recentCommits: List<GitHubCommitItem> = emptyList(),
    val recentPullRequests: List<GitHubPullRequestItem> = emptyList(),
    val notifyChannelId: Int? = null,
    val channels: List<GitHubChannelOption> = emptyList(),
    val openIssues: Int = 0,
    val recentIssues: List<GitHubIssueItem> = emptyList(),
    val repositoryId: Int? = null,
    val repositories: List<GitHubLinkedRepo> = emptyList()
)

@Serializable
data class GitHubLinkedRepo(
    val id: Int,
    val fullName: String
)

@Serializable
data class GitHubIssueItem(
    val number: Int,
    val title: String,
    val state: String,
    val authorUsername: String,
    val createdAt: Long,
    val url: String
)

@Serializable
data class GitHubPullRequestPage(
    val items: List<GitHubPullRequestItem>,
    val hasMore: Boolean
)

@Serializable
data class GitHubChannelOption(
    val id: Int,
    val name: String
)

@Serializable
data class GitHubNotifyChannelRequest(
    val channelId: Int? = null
)

@Serializable
data class GitHubTaskLinkResponse(
    val kind: String,
    val ref: String,
    val title: String,
    val url: String,
    val createdAt: Long
)

@Serializable
data class GitHubContributorStats(
    val username: String,
    val commits: Int,
    val memberUserId: Int? = null,
    val avatarUrl: String? = null,
    val githubName: String? = null
)

@Serializable
data class GitHubDailyCount(
    val date: String,
    val count: Int
)

@Serializable
data class GitHubCommitItem(
    val sha: String,
    val message: String,
    val authorName: String?,
    val commitDate: Long,
    val url: String? = null,
    val ciStatus: String? = null
)

@Serializable
data class GitHubPullRequestItem(
    val number: Int,
    val title: String,
    val state: String,
    val authorUsername: String,
    val createdAt: Long,
    val mergedAt: Long?,
    val url: String? = null,
    val ciStatus: String? = null
)
