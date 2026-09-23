package plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubAutomationTest {

    @Test
    fun `taskReferences finds every mentioned task`() {
        assertEquals(setOf(12, 7), taskReferences("Refactor login for T-12 and t-7"))
    }

    @Test
    fun `taskReferences ignores ids embedded in other words`() {
        assertEquals(emptySet(), taskReferences("See PROJECT-T-12 and XT-4"))
    }

    @Test
    fun `closingTaskReferences matches closing keywords only`() {
        val message = "Fixes T-3, closes T-4, resolved: T-5, mentions T-6"
        assertEquals(setOf(3, 4, 5), closingTaskReferences(message))
    }

    @Test
    fun `closingTaskReferences ignores plain mentions`() {
        assertEquals(emptySet(), closingTaskReferences("Work on T-9 continues"))
    }

    @Test
    fun `weekly digest is skipped for a quiet week`() {
        assertNull(formatWeeklyDigest("acme/app", 0, 0, 0, 0, 0, emptyList()))
    }

    @Test
    fun `weekly digest summarises activity and contributors`() {
        val digest = formatWeeklyDigest("acme/app", 1, 2, 3, 4, 5, listOf("Asha" to 1))
        assertEquals(
            listOf(
                "Weekly GitHub digest for acme/app",
                "• 1 commit",
                "• 3 PRs merged, 2 opened",
                "• 4 issues opened, 5 closed",
                "Top contributors: Asha (1)"
            ).joinToString("\n"),
            digest
        )
    }
}
