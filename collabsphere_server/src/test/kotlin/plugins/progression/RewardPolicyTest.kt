package plugins.progression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RewardPolicyTest {
    private val policy = ProgressionCatalogs.current.policy
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private val assignee = 7
    private val creator = 3

    private fun facts(
        actor: Int = assignee,
        assigneeId: Int? = assignee,
        createdBy: Int = creator,
        previous: String = "IN_PROGRESS",
        newStatus: String = "DONE",
        deleted: Boolean = false,
        priority: String = "MEDIUM",
        prioritySetBy: Int? = creator,
        checklist: Int = 0,
        labels: List<String> = emptyList(),
        due: Long? = null,
        dueSetBy: Int? = creator,
        dueSetAt: Long? = now - 2 * day,
        teamApproved: Boolean = false
    ) = TaskCompletionFacts(
        actorId = actor, assigneeId = assigneeId, createdById = createdBy, isDeleted = deleted,
        previousStatus = previous, newStatus = newStatus, priority = priority, prioritySetBy = prioritySetBy,
        checklistSize = checklist, labels = labels, dueDate = due, dueDateSetBy = dueSetBy,
        dueDateSetAt = dueSetAt, completedAt = now, teamApproved = teamApproved
    )

    @Test
    fun `completing a teammate's task earns the base rule and collaboration credit`() {
        val decision = RewardPolicy.taskCompletion(facts(), policy)!!
        assertEquals(RuleKeys.TASK_COMPLETED, decision.ruleKey)
        assertEquals(setOf(BadgeCounters.TASKS_COMPLETED, BadgeCounters.COLLABORATIVE_ACTIVITIES), decision.counters)
    }

    @Test
    fun `only the assignee moving a live task into done qualifies`() {
        assertNull(RewardPolicy.taskCompletion(facts(actor = creator), policy), "not the assignee")
        assertNull(RewardPolicy.taskCompletion(facts(assigneeId = null), policy), "unassigned")
        assertNull(RewardPolicy.taskCompletion(facts(deleted = true), policy), "deleted")
        assertNull(RewardPolicy.taskCompletion(facts(previous = "DONE"), policy), "already done")
        assertNull(RewardPolicy.taskCompletion(facts(newStatus = "IN_PROGRESS"), policy), "not a completion")
    }

    @Test
    fun `self-made tasks use the capped rule and earn no collaboration credit`() {
        val decision = RewardPolicy.taskCompletion(facts(createdBy = assignee), policy)!!
        assertEquals(RuleKeys.TASK_SELF_COMPLETED, decision.ruleKey)
        assertFalse(BadgeCounters.COLLABORATIVE_ACTIVITIES in decision.counters)
    }

    @Test
    fun `a team-approved task the proposer completes is not self-made`() {
        val decision = RewardPolicy.taskCompletion(facts(createdBy = assignee, teamApproved = true), policy)!!
        assertEquals(RuleKeys.TASK_COMPLETED, decision.ruleKey)
    }

    @Test
    fun `complexity needs high priority set by someone else and a checklist`() {
        assertEquals(RuleKeys.TASK_COMPLETED_COMPLEX,
            RewardPolicy.taskCompletion(facts(priority = "HIGH", checklist = 3), policy)!!.ruleKey)
        assertEquals(RuleKeys.TASK_COMPLETED,
            RewardPolicy.taskCompletion(facts(priority = "HIGH", prioritySetBy = assignee, checklist = 5), policy)!!.ruleKey,
            "assignee raised the priority themselves")
        assertEquals(RuleKeys.TASK_COMPLETED,
            RewardPolicy.taskCompletion(facts(priority = "HIGH", checklist = 2), policy)!!.ruleKey)
        assertEquals(RuleKeys.TASK_COMPLETED_COMPLEX,
            RewardPolicy.taskCompletion(facts(priority = "HIGH", prioritySetBy = null, checklist = 3), policy)!!.ruleKey,
            "a row older than the setter column falls back to its creator")
    }

    @Test
    fun `reliability labels earn guardian credit only on teammates' tasks`() {
        val assigned = RewardPolicy.taskCompletion(facts(labels = listOf(" Bug ")), policy)!!
        assertEquals(RuleKeys.TASK_RELIABILITY, assigned.ruleKey)
        assertTrue(BadgeCounters.QUALITY_IMPROVEMENTS in assigned.counters)

        val self = RewardPolicy.taskCompletion(facts(createdBy = assignee, labels = listOf("bug")), policy)!!
        assertFalse(BadgeCounters.QUALITY_IMPROVEMENTS in self.counters)
    }

    @Test
    fun `one sp rule per activity picks the most valuable`() {
        val decision = RewardPolicy.taskCompletion(facts(priority = "HIGH", checklist = 3, labels = listOf("fix")), policy)!!
        assertEquals(RuleKeys.TASK_COMPLETED_COMPLEX, decision.ruleKey)
        assertTrue(BadgeCounters.QUALITY_IMPROVEMENTS in decision.counters, "badge counters still advance")
    }

    @Test
    fun `on time requires a due date not pushed by the assignee at the last moment`() {
        assertTrue(BadgeCounters.ON_TIME_COMPLETIONS in RewardPolicy.taskCompletion(facts(due = now + day), policy)!!.counters)
        assertTrue(BadgeCounters.ON_TIME_COMPLETIONS in RewardPolicy.taskCompletion(facts(due = now), policy)!!.counters, "exactly at the due time")
        assertFalse(BadgeCounters.ON_TIME_COMPLETIONS in RewardPolicy.taskCompletion(facts(due = now - 1), policy)!!.counters, "late")
        assertFalse(BadgeCounters.ON_TIME_COMPLETIONS in RewardPolicy.taskCompletion(facts(due = null), policy)!!.counters, "no due date")
        assertFalse(
            BadgeCounters.ON_TIME_COMPLETIONS in RewardPolicy.taskCompletion(facts(due = now + day, dueSetBy = assignee, dueSetAt = now - 60_000), policy)!!.counters,
            "assignee moved the deadline a minute ago"
        )
        assertTrue(
            BadgeCounters.ON_TIME_COMPLETIONS in RewardPolicy.taskCompletion(facts(due = now + day, dueSetBy = assignee, dueSetAt = now - 2 * day), policy)!!.counters,
            "assignee planned it well in advance"
        )
    }

    @Test
    fun `note approval needs a teammate's pin on a substantial live note`() {
        val long = "x".repeat(policy.noteMinLength)
        assertEquals(RuleKeys.NOTE_APPROVED, RewardPolicy.noteApproval(1, 2, true, false, long, policy)?.ruleKey)
        assertNull(RewardPolicy.noteApproval(1, 1, true, false, long, policy), "self-pin")
        assertNull(RewardPolicy.noteApproval(1, 2, false, false, long, policy), "unpin")
        assertNull(RewardPolicy.noteApproval(1, 2, true, true, long, policy), "deleted")
        assertNull(RewardPolicy.noteApproval(1, 2, true, false, "  short  ", policy), "too short")
    }
}
