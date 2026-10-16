package plugins

import com.collabsphere.dto.StandupDraft

internal data class StandupTask(
    val name: String,
    val status: String,
    val dueDate: Long?,
    val completedAt: Long?
)

internal data class StandupPr(
    val number: Int,
    val title: String,
    val repo: String,
    val state: String,
    val createdAt: Long,
    val mergedAt: Long?,
    val approved: Boolean
)

internal data class StandupCommit(val repo: String, val message: String)

internal object StandupComposer {
    const val MAX_LINES = 6
    const val MAX_BLOCKERS = 3
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val REVIEW_WAIT_MS = DAY_MS
    private const val MAX_TITLE = 80

    fun compose(
        tasks: List<StandupTask>,
        prs: List<StandupPr>,
        commits: List<StandupCommit>,
        since: Long,
        now: Long,
        today: Long,
        githubLinked: Boolean
    ): StandupDraft = StandupDraft(
        yesterday = cap(yesterday(tasks, prs, commits, since, now), MAX_LINES),
        today = cap(todayLines(tasks, today), MAX_LINES),
        blockers = cap(blockers(prs, now), MAX_BLOCKERS),
        githubLinked = githubLinked
    )

    private fun yesterday(tasks: List<StandupTask>, prs: List<StandupPr>, commits: List<StandupCommit>, since: Long, now: Long): List<String> {
        val lines = mutableListOf<String>()
        tasks.filter { it.status == "DONE" && it.completedAt != null && it.completedAt in since until now }
            .sortedBy { it.completedAt }
            .forEach { lines += "Completed ${quote(it.name)}" }
        prs.filter { it.mergedAt != null && it.mergedAt in since until now }
            .sortedBy { it.mergedAt }
            .forEach { lines += "Merged PR #${it.number} ${quote(it.title)} (${it.repo})" }
        prs.filter { it.mergedAt == null && it.createdAt in since until now }
            .sortedBy { it.createdAt }
            .forEach { lines += "Opened PR #${it.number} ${quote(it.title)} (${it.repo})" }
        commits.groupBy { it.repo }
            .toSortedMap()
            .forEach { (repo, repoCommits) ->
                lines += if (repoCommits.size == 1) {
                    "Committed ${quote(firstLine(repoCommits.single().message))} on $repo"
                } else {
                    "${repoCommits.size} commits on $repo"
                }
            }
        return lines
    }

    private fun todayLines(tasks: List<StandupTask>, today: Long): List<String> {
        val open = tasks.filter { it.status != "DONE" }
        val overdue = open.filter { it.dueDate != null && it.dueDate < today }.sortedBy { it.dueDate }
        val dueToday = open.filter { it.dueDate != null && it.dueDate >= today && it.dueDate < today + DAY_MS }
        val listed = (overdue + dueToday).toSet()
        val inProgress = open.filter { it.status == "IN_PROGRESS" && it !in listed }
        return overdue.map { "${quote(it.name)} (overdue)" } +
            dueToday.map { "${quote(it.name)} — due today" } +
            inProgress.map { "Continue ${quote(it.name)}" }
    }

    private fun blockers(prs: List<StandupPr>, now: Long): List<String> =
        prs.filter { it.state == "open" && it.mergedAt == null && !it.approved && it.createdAt <= now - REVIEW_WAIT_MS }
            .sortedBy { it.createdAt }
            .map { "PR #${it.number} ${quote(it.title)} is waiting for review (${it.repo})" }

    private fun cap(lines: List<String>, max: Int): List<String> =
        if (lines.size <= max) lines else lines.take(max - 1) + "…and ${lines.size - (max - 1)} more"

    private fun firstLine(message: String): String = message.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    private fun quote(text: String): String {
        val trimmed = text.trim().replace(Regex("\\s+"), " ")
        val short = if (trimmed.length > MAX_TITLE) trimmed.take(MAX_TITLE - 1).trimEnd() + "…" else trimmed
        return "“$short”"
    }
}
