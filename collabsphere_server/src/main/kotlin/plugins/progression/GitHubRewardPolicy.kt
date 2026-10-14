package plugins.progression

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant

enum class GitHubContributionKind { PR_AUTHORED, PR_MERGED_BY, REVIEW_APPROVED, ISSUE_RESOLVED, RELEASE_PUBLISHED, MILESTONE_COMPLETED }

/**
 * A rewardable fact extracted from a signature-verified GitHub webhook. The actor is GitHub's
 * numeric user id; mapping it to a CollabSphere user (and checking workspace membership) happens
 * against verified identities only.
 */
data class GitHubContribution(
    val kind: GitHubContributionKind,
    val githubRepoId: Long,
    val actorGithubId: Long,
    /** Identifies the activity itself, so webhook retries and re-deliveries count once. */
    val activityKey: String,
    val referenceType: String,
    val referenceId: String,
    val occurredAt: Long,
    val decision: RewardDecision,
    /** For PR events: the PR number (used to credit stored approvals on merge). */
    val prNumber: Int? = null,
    /** For review events: whether the PR has already merged (credit now) or not (credit on merge). */
    val prAlreadyMerged: Boolean = false
)

/** Pure parsing of webhook payloads into contributions. Anything unrecognised or ineligible yields nothing. */
object GitHubRewardPolicy {
    private val json = Json { ignoreUnknownKeys = true }

    fun contributions(eventType: String, payloadJson: String, policy: ProgressionPolicy, now: Long = System.currentTimeMillis()): List<GitHubContribution> {
        val payload = runCatching { json.parseToJsonElement(payloadJson).jsonObject }.getOrNull() ?: return emptyList()
        val repoId = payload.obj("repository")?.long("id") ?: return emptyList()
        return when (eventType) {
            "pull_request" -> pullRequest(payload, repoId, policy, now)
            "pull_request_review" -> review(payload, repoId, now)
            "issues" -> issue(payload, repoId, now)
            "release" -> release(payload, repoId, now)
            "milestone" -> milestone(payload, repoId, now)
            else -> emptyList()
        }
    }

    private fun pullRequest(payload: JsonObject, repoId: Long, policy: ProgressionPolicy, now: Long): List<GitHubContribution> {
        if (payload.string("action") != "closed") return emptyList()
        val pr = payload.obj("pull_request") ?: return emptyList()
        if (pr.bool("merged") != true) return emptyList()
        val number = pr.int("number") ?: return emptyList()
        val author = pr.obj("user") ?: return emptyList()
        val authorId = author.long("id") ?: return emptyList()
        if (author.isBot()) return emptyList()
        // An empty pull request is not a code contribution.
        val changed = (pr.long("additions") ?: 0) + (pr.long("deletions") ?: 0)
        if (changed <= 0) return emptyList()
        val mergedAt = pr.time("merged_at") ?: now
        val reference = "$repoId#$number"

        val labels = pr["labels"]?.let { runCatching { it.jsonArray }.getOrNull() }
            ?.mapNotNull { it.jsonObject.string("name") }.orEmpty()
        val reliability = labels.any { label -> policy.reliabilityLabels.any { it.equals(label.trim(), ignoreCase = true) } }
        val authorCounters = buildSet {
            add(BadgeCounters.ACCEPTED_CODE_CONTRIBUTIONS)
            if (reliability) add(BadgeCounters.QUALITY_IMPROVEMENTS)
        }
        val result = mutableListOf(
            GitHubContribution(
                kind = GitHubContributionKind.PR_AUTHORED,
                githubRepoId = repoId,
                actorGithubId = authorId,
                activityKey = "gh-pr:$repoId:$number",
                referenceType = "PULL_REQUEST",
                referenceId = reference,
                occurredAt = mergedAt,
                decision = RewardDecision(RuleKeys.PR_MERGED, authorCounters),
                prNumber = number
            )
        )
        // Merging a teammate's pull request is maintainer work; merging your own is not counted again.
        val merger = pr.obj("merged_by")
        val mergerId = merger?.long("id")
        if (merger != null && mergerId != null && mergerId != authorId && !merger.isBot()) {
            result += GitHubContribution(
                kind = GitHubContributionKind.PR_MERGED_BY,
                githubRepoId = repoId,
                actorGithubId = mergerId,
                activityKey = "gh-pr-merge:$repoId:$number",
                referenceType = "PULL_REQUEST",
                referenceId = reference,
                occurredAt = mergedAt,
                decision = RewardDecision(RuleKeys.PR_MERGED_AS_MAINTAINER, setOf(BadgeCounters.PRS_MERGED_AS_MAINTAINER)),
                prNumber = number
            )
        }
        return result
    }

    private fun review(payload: JsonObject, repoId: Long, now: Long): List<GitHubContribution> {
        if (payload.string("action") != "submitted") return emptyList()
        val review = payload.obj("review") ?: return emptyList()
        if (!review.string("state").equals("approved", ignoreCase = true)) return emptyList()
        val reviewer = review.obj("user") ?: return emptyList()
        val reviewerId = reviewer.long("id") ?: return emptyList()
        if (reviewer.isBot()) return emptyList()
        val pr = payload.obj("pull_request") ?: return emptyList()
        val number = pr.int("number") ?: return emptyList()
        // Approving your own pull request is not a review.
        if (pr.obj("user")?.long("id") == reviewerId) return emptyList()
        return listOf(reviewContribution(repoId, number, reviewerId, review.time("submitted_at") ?: now, alreadyMerged = pr.time("merged_at") != null))
    }

    fun reviewContribution(repoId: Long, prNumber: Int, reviewerGithubId: Long, occurredAt: Long, alreadyMerged: Boolean) = GitHubContribution(
        kind = GitHubContributionKind.REVIEW_APPROVED,
        githubRepoId = repoId,
        actorGithubId = reviewerGithubId,
        activityKey = "gh-review:$repoId:$prNumber:$reviewerGithubId",
        referenceType = "PULL_REQUEST_REVIEW",
        referenceId = "$repoId#$prNumber",
        occurredAt = occurredAt,
        decision = RewardDecision(RuleKeys.REVIEW_ACCEPTED, setOf(BadgeCounters.COLLABORATIVE_ACTIVITIES)),
        prNumber = prNumber,
        prAlreadyMerged = alreadyMerged
    )

    private fun issue(payload: JsonObject, repoId: Long, now: Long): List<GitHubContribution> {
        if (payload.string("action") != "closed") return emptyList()
        val issue = payload.obj("issue") ?: return emptyList()
        if (issue["pull_request"] != null) return emptyList()
        // "not_planned" closes are triage, not resolution.
        if (issue.string("state_reason") != "completed") return emptyList()
        val sender = payload.obj("sender") ?: return emptyList()
        val closerId = sender.long("id") ?: return emptyList()
        if (sender.isBot()) return emptyList()
        // Opening and closing your own issue would be free points.
        if (issue.obj("user")?.long("id") == closerId) return emptyList()
        val number = issue.int("number") ?: return emptyList()
        return listOf(
            GitHubContribution(
                kind = GitHubContributionKind.ISSUE_RESOLVED,
                githubRepoId = repoId,
                actorGithubId = closerId,
                activityKey = "gh-issue:$repoId:$number",
                referenceType = "ISSUE",
                referenceId = "$repoId#$number",
                occurredAt = issue.time("closed_at") ?: now,
                decision = RewardDecision(RuleKeys.ISSUE_RESOLVED, setOf(BadgeCounters.ISSUES_RESOLVED))
            )
        )
    }

    private fun release(payload: JsonObject, repoId: Long, now: Long): List<GitHubContribution> {
        if (payload.string("action") != "published") return emptyList()
        val release = payload.obj("release") ?: return emptyList()
        if (release.bool("draft") == true || release.bool("prerelease") == true) return emptyList()
        val releaseId = release.long("id") ?: return emptyList()
        val author = release.obj("author") ?: return emptyList()
        val authorId = author.long("id") ?: return emptyList()
        if (author.isBot()) return emptyList()
        return listOf(
            GitHubContribution(
                kind = GitHubContributionKind.RELEASE_PUBLISHED,
                githubRepoId = repoId,
                actorGithubId = authorId,
                activityKey = "gh-release:$releaseId",
                referenceType = "RELEASE",
                referenceId = release.string("tag_name") ?: releaseId.toString(),
                occurredAt = release.time("published_at") ?: now,
                decision = RewardDecision(RuleKeys.RELEASE_PUBLISHED, setOf(BadgeCounters.RELEASES_PUBLISHED))
            )
        )
    }

    private fun milestone(payload: JsonObject, repoId: Long, now: Long): List<GitHubContribution> {
        if (payload.string("action") != "closed") return emptyList()
        val milestone = payload.obj("milestone") ?: return emptyList()
        // An empty milestone delivers nothing.
        if ((milestone.int("closed_issues") ?: 0) < 1) return emptyList()
        val milestoneId = milestone.long("id") ?: return emptyList()
        val sender = payload.obj("sender") ?: return emptyList()
        val closerId = sender.long("id") ?: return emptyList()
        if (sender.isBot()) return emptyList()
        return listOf(
            GitHubContribution(
                kind = GitHubContributionKind.MILESTONE_COMPLETED,
                githubRepoId = repoId,
                actorGithubId = closerId,
                activityKey = "gh-milestone:$repoId:$milestoneId",
                referenceType = "MILESTONE",
                referenceId = milestone.string("title") ?: milestoneId.toString(),
                occurredAt = milestone.time("closed_at") ?: now,
                decision = RewardDecision(RuleKeys.MILESTONE_COMPLETED, setOf(BadgeCounters.MILESTONES_COMPLETED))
            )
        )
    }

    private fun JsonObject.obj(key: String): JsonObject? = runCatching { this[key]?.jsonObject }.getOrNull()
    private fun JsonObject.string(key: String): String? = runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
    private fun JsonObject.long(key: String): Long? = runCatching { this[key]?.jsonPrimitive?.longOrNull }.getOrNull()
    private fun JsonObject.int(key: String): Int? = runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()
    private fun JsonObject.bool(key: String): Boolean? = runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull()
    private fun JsonObject.time(key: String): Long? = string(key)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    private fun JsonObject.isBot(): Boolean = string("type").equals("Bot", ignoreCase = true)
}
