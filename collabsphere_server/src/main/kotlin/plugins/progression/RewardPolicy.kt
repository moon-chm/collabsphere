package plugins.progression

/** What a qualifying activity earns: at most one SP rule, plus any number of badge counters. */
data class RewardDecision(
    val ruleKey: String,
    val counters: Set<String>
)

/** Authoritative, server-side facts about a task at the moment its status changes. */
data class TaskCompletionFacts(
    val actorId: Int,
    val assigneeId: Int?,
    val createdById: Int,
    val isDeleted: Boolean,
    val previousStatus: String?,
    val newStatus: String,
    val priority: String,
    /** Who last set the priority; null for rows older than this column (treated as the creator). */
    val prioritySetBy: Int?,
    val checklistSize: Int,
    val labels: List<String>,
    val dueDate: Long?,
    /** Who last set the due date; null for rows older than this column (treated as the creator). */
    val dueDateSetBy: Int?,
    val dueDateSetAt: Long?,
    val completedAt: Long,
    /** Created through a task-vote proposal that the team approved (not a solo auto-approval). */
    val teamApproved: Boolean
)

/** Pure eligibility rules. Anything not explicitly rewarded here earns nothing. */
object RewardPolicy {
    const val DONE = "DONE"

    /**
     * Decision for a task status change, or null when it earns nothing: only the assignee moving a
     * live task into DONE qualifies. Each task is rewarded at most once ever (enforced by the
     * caller's idempotency key), so toggling DONE off and on again cannot re-earn.
     */
    fun taskCompletion(facts: TaskCompletionFacts, policy: ProgressionPolicy): RewardDecision? {
        if (facts.newStatus != DONE || facts.previousStatus == DONE) return null
        if (facts.isDeleted) return null
        val assignee = facts.assigneeId ?: return null
        if (facts.actorId != assignee) return null

        // A task someone else created (or the team voted in) is real assigned work; one you made for
        // yourself is rewarded only under the self-made daily cap and earns no collaboration credit.
        val assignedByOther = facts.createdById != assignee || facts.teamApproved
        val reliability = facts.labels.any { label ->
            policy.reliabilityLabels.any { it.equals(label.trim(), ignoreCase = true) }
        }
        val complex = facts.priority.equals(policy.complexPriority, ignoreCase = true) &&
            (facts.prioritySetBy ?: facts.createdById) != assignee &&
            facts.checklistSize >= policy.complexMinChecklistItems

        val counters = mutableSetOf(BadgeCounters.TASKS_COMPLETED)
        if (isOnTime(facts, assignee, policy)) counters += BadgeCounters.ON_TIME_COMPLETIONS
        if (!assignedByOther) return RewardDecision(RuleKeys.TASK_SELF_COMPLETED, counters)

        counters += BadgeCounters.COLLABORATIVE_ACTIVITIES
        if (reliability) counters += BadgeCounters.QUALITY_IMPROVEMENTS
        // One SP rule per activity: the most valuable that applies.
        val rule = when {
            complex -> RuleKeys.TASK_COMPLETED_COMPLEX
            reliability -> RuleKeys.TASK_RELIABILITY
            else -> RuleKeys.TASK_COMPLETED
        }
        return RewardDecision(rule, counters)
    }

    /**
     * On time means completed at or before the due date. A due date the assignee set for themselves
     * only counts if it was set well before completion, so it cannot be pushed out at the last moment.
     */
    fun isOnTime(facts: TaskCompletionFacts, assignee: Int, policy: ProgressionPolicy): Boolean {
        val due = facts.dueDate ?: return false
        if (facts.completedAt > due) return false
        val setBy = facts.dueDateSetBy ?: facts.createdById
        if (setBy != assignee) return true
        val setAt = facts.dueDateSetAt ?: return false
        return facts.completedAt - setAt >= policy.onTimeSelfSetMinLeadMs
    }

    /** A substantial, live note pinned by someone other than its author counts as approved documentation. */
    fun noteApproval(
        authorId: Int,
        pinnerId: Int,
        pinned: Boolean,
        isDeleted: Boolean,
        description: String,
        policy: ProgressionPolicy
    ): RewardDecision? {
        if (!pinned || isDeleted || pinnerId == authorId) return null
        if (description.trim().length < policy.noteMinLength) return null
        return RewardDecision(RuleKeys.NOTE_APPROVED, setOf(BadgeCounters.KNOWLEDGE_CONTRIBUTIONS))
    }
}

object RuleKeys {
    const val TASK_COMPLETED = "TASK_COMPLETED"
    const val TASK_COMPLETED_COMPLEX = "TASK_COMPLETED_COMPLEX"
    const val TASK_RELIABILITY = "TASK_RELIABILITY"
    const val TASK_SELF_COMPLETED = "TASK_SELF_COMPLETED"
    const val NOTE_APPROVED = "NOTE_APPROVED"
    const val PR_MERGED = "PR_MERGED"
    const val PR_MERGED_AS_MAINTAINER = "PR_MERGED_AS_MAINTAINER"
    const val REVIEW_ACCEPTED = "REVIEW_ACCEPTED"
    const val ISSUE_RESOLVED = "ISSUE_RESOLVED"
    const val RELEASE_PUBLISHED = "RELEASE_PUBLISHED"
    const val MILESTONE_COMPLETED = "MILESTONE_COMPLETED"
    const val MESSAGE_MARKED_HELPFUL = "MESSAGE_MARKED_HELPFUL"
    const val PEER_RECOGNITION = "PEER_RECOGNITION"
}
