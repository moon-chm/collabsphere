package plugins.progression

import com.collabsphere.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import plugins.isMember

/**
 * Applies career rewards for a signature-verified GitHub webhook. Must run inside the caller's
 * transaction (the one that marks the delivery processed), so a retried delivery is both
 * re-processed and re-rewarded idempotently: every activity key is recorded at most once.
 */
object GitHubRewards {

    fun apply(
        eventType: String,
        payloadJson: String,
        catalog: ProgressionCatalog = ProgressionCatalogs.current,
        now: Long = System.currentTimeMillis()
    ): List<PendingNotification> {
        val notifications = mutableListOf<PendingNotification>()
        for (contribution in GitHubRewardPolicy.contributions(eventType, payloadJson, catalog.policy, now)) {
            when (contribution.kind) {
                GitHubContributionKind.REVIEW_APPROVED -> {
                    // Reviews only count once their pull request is accepted: remember the approval,
                    // and credit it now only if the PR has already merged.
                    GitHubPrApprovalsTable.insertIgnore {
                        it[githubRepoId] = contribution.githubRepoId
                        it[prNumber] = contribution.prNumber!!
                        it[reviewerGithubId] = contribution.actorGithubId
                        it[approvedAt] = contribution.occurredAt
                    }
                    if (contribution.prAlreadyMerged) notifications += credit(contribution, catalog, now)
                }
                GitHubContributionKind.PR_AUTHORED -> {
                    notifications += credit(contribution, catalog, now)
                    val approvals = GitHubPrApprovalsTable.selectAll().where {
                        (GitHubPrApprovalsTable.githubRepoId eq contribution.githubRepoId) and
                            (GitHubPrApprovalsTable.prNumber eq contribution.prNumber!!)
                    }.map { it[GitHubPrApprovalsTable.reviewerGithubId] }
                    approvals.filter { it != contribution.actorGithubId }.forEach { reviewer ->
                        notifications += credit(
                            GitHubRewardPolicy.reviewContribution(
                                contribution.githubRepoId, contribution.prNumber!!, reviewer,
                                occurredAt = contribution.occurredAt, alreadyMerged = true
                            ),
                            catalog, now
                        )
                    }
                }
                else -> notifications += credit(contribution, catalog, now)
            }
        }
        return notifications
    }

    private fun credit(contribution: GitHubContribution, catalog: ProgressionCatalog, now: Long): List<PendingNotification> {
        val userId = userFor(contribution.actorGithubId) ?: return emptyList()
        val workspaceId = workspaceFor(contribution.githubRepoId, userId) ?: return emptyList()
        return ProgressionService.record(
            ContributionEvent(
                idempotencyKey = contribution.activityKey,
                userId = userId,
                eventType = "GITHUB_${contribution.kind.name}",
                source = "GITHUB",
                referenceType = contribution.referenceType,
                referenceId = contribution.referenceId,
                workspaceId = workspaceId,
                occurredAt = contribution.occurredAt,
                decision = contribution.decision
            ),
            catalog,
            now
        ).notifications
    }

    /**
     * The CollabSphere user behind a GitHub numeric id: a member's verified identity link first, then
     * an owner's OAuth-verified GitHub App connection. Ambiguous matches are not guessed.
     */
    internal fun userFor(githubUserId: Long): Int? {
        UserGitHubIdentitiesTable.select(UserGitHubIdentitiesTable.userId)
            .where { UserGitHubIdentitiesTable.githubUserId eq githubUserId }
            .singleOrNull()?.let { return it[UserGitHubIdentitiesTable.userId] }
        val owners = GitHubConnectionsTable.select(GitHubConnectionsTable.userId)
            .where { GitHubConnectionsTable.githubUserId eq githubUserId }
            .map { it[GitHubConnectionsTable.userId] }
            .distinct()
        return owners.singleOrNull()
    }

    /** A live workspace that links this repository and that the user belongs to (lowest id, for determinism). */
    private fun workspaceFor(githubRepoId: Long, userId: Int): Int? =
        (GitHubRepositoriesTable innerJoin WorkspacesTable)
            .select(GitHubRepositoriesTable.workspaceId)
            .where { (GitHubRepositoriesTable.githubRepoId eq githubRepoId) and (WorkspacesTable.isDeleted eq false) }
            .map { it[GitHubRepositoriesTable.workspaceId] }
            .distinct()
            .sorted()
            .firstOrNull { isMember(userId, it) }
}
