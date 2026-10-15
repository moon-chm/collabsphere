package com.collabsphere.app.dto.workspace

import kotlinx.serialization.Serializable

/** GET /api/workspace/{id}/analytics — who contributed what (chat and DMs are not counted). */
@Serializable
data class WorkspaceAnalyticsResponse(
    val range: String,
    val since: Long? = null,
    val generatedAt: Long,
    val hasGitHub: Boolean = false,
    val totals: ContributionCounts = ContributionCounts(),
    /** Every current member, most active first. */
    val members: List<MemberContribution> = emptyList(),
    /** GitHub authors not linked to any member. */
    val unlinkedGitHub: List<UnlinkedGitHubContributor> = emptyList()
)

@Serializable
data class ContributionCounts(
    val tasksCreated: Int = 0,
    val tasksCompleted: Int = 0,
    /** Open tasks currently assigned (a snapshot, not limited by the range). */
    val tasksOpen: Int = 0,
    val notesWritten: Int = 0,
    val filesUploaded: Int = 0,
    val fileBytes: Long = 0,
    val commits: Int = 0,
    val pullRequestsOpened: Int = 0,
    val pullRequestsMerged: Int = 0,
    val issuesOpened: Int = 0
) {
    val taskWork: Int get() = tasksCreated + tasksCompleted
    val gitHubWork: Int get() = commits + pullRequestsOpened + pullRequestsMerged + issuesOpened
}

@Serializable
data class MemberContribution(
    val userId: Int,
    val username: String,
    val avatarUrl: String? = null,
    val githubLogin: String? = null,
    val counts: ContributionCounts = ContributionCounts(),
    val total: Int = 0
)

@Serializable
data class UnlinkedGitHubContributor(
    val name: String,
    val commits: Int = 0,
    val pullRequestsOpened: Int = 0,
    val pullRequestsMerged: Int = 0,
    val issuesOpened: Int = 0,
    val total: Int = 0
)
