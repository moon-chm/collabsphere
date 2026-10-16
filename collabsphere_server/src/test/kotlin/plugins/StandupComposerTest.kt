package plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StandupComposerTest {

    private val day = StandupComposer.DAY_MS
    private val today = 20_000L * day
    private val now = today + 9 * 60 * 60 * 1000L
    private val since = today - day

    private fun compose(
        tasks: List<StandupTask> = emptyList(),
        prs: List<StandupPr> = emptyList(),
        commits: List<StandupCommit> = emptyList()
    ) = StandupComposer.compose(tasks, prs, commits, since, now, today, githubLinked = true)

    private fun task(name: String, status: String = "TODO", due: Long? = null, completedAt: Long? = null) =
        StandupTask(name, status, due, completedAt)

    private fun pr(
        number: Int,
        state: String = "open",
        createdAt: Long = now - 2 * day,
        mergedAt: Long? = null,
        approved: Boolean = false
    ) = StandupPr(number, "PR $number", "acme/app", state, createdAt, mergedAt, approved)

    @Test
    fun `yesterday lists completed tasks, merged and opened PRs, and commits per repo`() {
        val draft = compose(
            tasks = listOf(
                task("Fix login", status = "DONE", completedAt = since + 1000),
                task("Old work", status = "DONE", completedAt = since - 1000)
            ),
            prs = listOf(
                pr(42, state = "closed", createdAt = since - day, mergedAt = since + 5000),
                pr(43, createdAt = since + 6000)
            ),
            commits = listOf(
                StandupCommit("acme/app", "feat: one"),
                StandupCommit("acme/app", "fix: two"),
                StandupCommit("acme/api", "chore: bump deps\n\nlong body")
            )
        )
        assertEquals(
            listOf(
                "Completed “Fix login”",
                "Merged PR #42 “PR 42” (acme/app)",
                "Opened PR #43 “PR 43” (acme/app)",
                "Committed “chore: bump deps” on acme/api",
                "2 commits on acme/app"
            ),
            draft.yesterday
        )
    }

    @Test
    fun `today orders overdue, due today, then in-progress without duplicates`() {
        val draft = compose(
            tasks = listOf(
                task("In flight", status = "IN_PROGRESS"),
                task("Ship notes", due = today),
                task("Late thing", due = today - day),
                task("Due and active", status = "IN_PROGRESS", due = today),
                task("Next week", due = today + 7 * day),
                task("Done today", status = "DONE", due = today, completedAt = now - 1000)
            )
        )
        assertEquals(
            listOf(
                "“Late thing” (overdue)",
                "“Ship notes” — due today",
                "“Due and active” — due today",
                "Continue “In flight”"
            ),
            draft.today
        )
    }

    @Test
    fun `blockers are my open unapproved PRs older than a day`() {
        val draft = compose(
            prs = listOf(
                pr(1, createdAt = now - 3 * day),
                pr(2, createdAt = now - 3 * day, approved = true),
                pr(3, createdAt = now - 1000),
                pr(4, state = "closed", createdAt = now - 3 * day)
            )
        )
        assertEquals(listOf("PR #1 “PR 1” is waiting for review (acme/app)"), draft.blockers)
    }

    @Test
    fun `long sections are capped with a summary line`() {
        val tasks = (1..10).map { task("Task $it", status = "DONE", completedAt = since + it) }
        val draft = compose(tasks = tasks)
        assertEquals(StandupComposer.MAX_LINES, draft.yesterday.size)
        assertEquals("…and 5 more", draft.yesterday.last())
    }

    @Test
    fun `long titles are shortened and whitespace collapsed`() {
        val draft = compose(tasks = listOf(task("a  b\n" + "x".repeat(200), status = "IN_PROGRESS")))
        val line = draft.today.single()
        assertTrue(line.startsWith("Continue “a b x"))
        assertTrue(line.length < 100)
    }

    @Test
    fun `an empty day yields empty sections`() {
        val draft = compose()
        assertTrue(draft.yesterday.isEmpty() && draft.today.isEmpty() && draft.blockers.isEmpty())
    }
}
