package com.collabsphere.dto

import kotlinx.serialization.Serializable

/** Who contributed what to a workspace over a time range (chat and DMs are deliberately not counted). */
@Serializable
data class WorkspaceAnalyticsResponse(
    /** "7d", "30d", "90d" or "all". */
    val range: String,
    /** Start of the range (epoch ms); null for "all". */
    val since: Long?,
    val generatedAt: Long,
    /** False when no GitHub repository is linked, so the client can hide the GitHub columns. */
    val hasGitHub: Boolean,
    val totals: ContributionCounts,
    /** Every current member, most active first; members with no activity are included with zeros. */
    val members: List<MemberContribution>,
    /** GitHub authors in the range who are not linked to any member, most active first. */
    val unlinkedGitHub: List<UnlinkedGitHubContributor>
)

@Serializable
data class ContributionCounts(
    val tasksCreated: Int = 0,
    /** Tasks completed in the range while assigned to the member. */
    val tasksCompleted: Int = 0,
    /** Currently open tasks assigned to the member (a snapshot, not limited by the range). */
    val tasksOpen: Int = 0,
    val notesWritten: Int = 0,
    val filesUploaded: Int = 0,
    val fileBytes: Long = 0,
    val commits: Int = 0,
    val pullRequestsOpened: Int = 0,
    val pullRequestsMerged: Int = 0,
    val issuesOpened: Int = 0
) {
    /** Everything the member produced in the range; open tasks and bytes are context, not output. */
    val total: Int
        get() = tasksCreated + tasksCompleted + notesWritten + filesUploaded +
            commits + pullRequestsOpened + pullRequestsMerged + issuesOpened
}

@Serializable
data class MemberContribution(
    val userId: Int,
    val username: String,
    val avatarUrl: String,
    /** Their linked (verified) GitHub login, if any. */
    val githubLogin: String? = null,
    val counts: ContributionCounts,
    val total: Int
)

@Serializable
data class UnlinkedGitHubContributor(
    /** GitHub login when GitHub could tie the activity to an account; otherwise the git author name. */
    val name: String,
    val commits: Int = 0,
    val pullRequestsOpened: Int = 0,
    val pullRequestsMerged: Int = 0,
    val issuesOpened: Int = 0,
    val total: Int
)
