package plugins.progression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubRewardPolicyTest {
    private val policy = ProgressionCatalogs.current.policy
    private val repo = """"repository":{"id":555,"full_name":"acme/app"}"""

    private fun user(id: Long, type: String = "User") = """{"id":$id,"login":"u$id","type":"$type"}"""

    private fun pr(
        action: String = "closed", merged: Boolean = true, author: String = user(1), mergedBy: String? = user(2),
        additions: Int = 10, deletions: Int = 2, labels: String = "[]"
    ) = """{"action":"$action",$repo,"pull_request":{"number":7,"merged":$merged,"user":$author,
        "merged_by":${mergedBy ?: "null"},"additions":$additions,"deletions":$deletions,"labels":$labels,
        "merged_at":"2026-10-01T10:00:00Z"}}"""

    private fun contributions(event: String, payload: String) = GitHubRewardPolicy.contributions(event, payload, policy)

    @Test
    fun `a merged pull request credits its author and a different merger`() {
        val result = contributions("pull_request", pr())
        assertEquals(listOf(GitHubContributionKind.PR_AUTHORED, GitHubContributionKind.PR_MERGED_BY), result.map { it.kind })
        val authored = result.first()
        assertEquals(1L, authored.actorGithubId)
        assertEquals("gh-pr:555:7", authored.activityKey)
        assertEquals(RuleKeys.PR_MERGED, authored.decision.ruleKey)
        assertEquals(setOf(BadgeCounters.ACCEPTED_CODE_CONTRIBUTIONS), authored.decision.counters)
        assertEquals(2L, result[1].actorGithubId)
        assertEquals(1_790_848_800_000L, authored.occurredAt, "the authoritative merge time")
    }

    @Test
    fun `unmerged, empty, bot and self-merged pull requests are not rewarded beyond what is due`() {
        assertTrue(contributions("pull_request", pr(merged = false)).isEmpty(), "closed without merge")
        assertTrue(contributions("pull_request", pr(action = "opened")).isEmpty())
        assertTrue(contributions("pull_request", pr(additions = 0, deletions = 0)).isEmpty(), "empty diff")
        assertTrue(contributions("pull_request", pr(author = user(1, "Bot"))).isEmpty(), "bot author")
        assertEquals(listOf(GitHubContributionKind.PR_AUTHORED), contributions("pull_request", pr(mergedBy = user(1))).map { it.kind }, "self-merge")
    }

    @Test
    fun `reliability labels on a merged pull request count toward guardian`() {
        val authored = contributions("pull_request", pr(labels = """[{"name":"Bug"}]""")).first()
        assertTrue(BadgeCounters.QUALITY_IMPROVEMENTS in authored.decision.counters)
    }

    private fun review(state: String, reviewer: Long, mergedAt: String? = null) =
        """{"action":"submitted",$repo,"review":{"state":"$state","user":${user(reviewer)},"submitted_at":"2026-10-01T09:00:00Z"},
        "pull_request":{"number":7,"user":${user(1)},"merged_at":${mergedAt?.let { "\"$it\"" } ?: "null"}}}"""

    @Test
    fun `only a teammate's approval is a review contribution`() {
        val approval = contributions("pull_request_review", review("approved", 3)).single()
        assertEquals(GitHubContributionKind.REVIEW_APPROVED, approval.kind)
        assertFalse(approval.prAlreadyMerged)
        assertTrue(contributions("pull_request_review", review("approved", 3, "2026-10-01T10:00:00Z")).single().prAlreadyMerged)
        assertTrue(contributions("pull_request_review", review("changes_requested", 3)).isEmpty())
        assertTrue(contributions("pull_request_review", review("approved", 1)).isEmpty(), "self-approval")
    }

    private fun issue(reason: String, closer: Long, author: Long = 1) =
        """{"action":"closed",$repo,"sender":${user(closer)},"issue":{"number":9,"state_reason":"$reason","user":${user(author)},"closed_at":"2026-10-02T00:00:00Z"}}"""

    @Test
    fun `issues count when someone else closes them as completed`() {
        val resolved = contributions("issues", issue("completed", 4)).single()
        assertEquals(4L, resolved.actorGithubId)
        assertEquals("gh-issue:555:9", resolved.activityKey)
        assertTrue(contributions("issues", issue("not_planned", 4)).isEmpty())
        assertTrue(contributions("issues", issue("completed", 1)).isEmpty(), "closing your own issue")
    }

    private fun release(draft: Boolean = false, prerelease: Boolean = false, action: String = "published") =
        """{"action":"$action",$repo,"release":{"id":31,"tag_name":"v1.0","draft":$draft,"prerelease":$prerelease,"author":${user(5)}}}"""

    @Test
    fun `only published full releases count`() {
        assertEquals("gh-release:31", contributions("release", release()).single().activityKey)
        assertTrue(contributions("release", release(draft = true)).isEmpty())
        assertTrue(contributions("release", release(prerelease = true)).isEmpty())
        assertTrue(contributions("release", release(action = "created")).isEmpty())
    }

    private fun milestone(closedIssues: Int) =
        """{"action":"closed",$repo,"sender":${user(6)},"milestone":{"id":12,"title":"Beta","closed_issues":$closedIssues}}"""

    @Test
    fun `an empty milestone delivers nothing`() {
        assertEquals("gh-milestone:555:12", contributions("milestone", milestone(3)).single().activityKey)
        assertTrue(contributions("milestone", milestone(0)).isEmpty())
    }

    @Test
    fun `malformed or unrelated payloads are ignored`() {
        assertTrue(contributions("pull_request", "not json").isEmpty())
        assertTrue(contributions("push", pr()).isEmpty())
        assertTrue(contributions("pull_request", """{"action":"closed"}""").isEmpty(), "no repository")
    }
}
