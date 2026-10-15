package plugins

import com.collabsphere.dto.ContributionCounts
import com.collabsphere.dto.MemberContribution
import com.collabsphere.dto.UnlinkedGitHubContributor
import com.collabsphere.dto.WorkspaceAnalyticsResponse
import com.collabsphere.model.GitHubCommitsTable
import com.collabsphere.model.GitHubIssuesTable
import com.collabsphere.model.GitHubPullRequestsTable
import com.collabsphere.model.GitHubRepositoriesTable
import com.collabsphere.model.LocalFilesTable
import com.collabsphere.model.NotesTable
import com.collabsphere.model.TasksTable
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Coalesce
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AnalyticsRoutes")

/** Ranges the analytics screen offers, in days; "all" has no lower bound. */
internal val ANALYTICS_RANGES = mapOf("7d" to 7L, "30d" to 30L, "90d" to 90L, "all" to null)
internal const val DEFAULT_ANALYTICS_RANGE = "30d"
private const val DAY_MS = 24L * 60 * 60 * 1000
private const val MAX_UNLINKED = 10

/**
 * Workspace contribution analytics. Mounted inside `authenticate("auth-jwt")`.
 * Every member may see every member's numbers.
 */
internal fun Route.analyticsRoutes() {
    get("/api/workspace/{workspaceId}/analytics") {
        val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid workspace ID")
        val range = call.request.queryParameters["range"] ?: DEFAULT_ANALYTICS_RANGE
        if (range !in ANALYTICS_RANGES) {
            return@get call.respond(HttpStatusCode.BadRequest, "range must be one of ${ANALYTICS_RANGES.keys.joinToString()}")
        }
        val actingUserId = call.authenticatedUserId()
        try {
            val result = dbReadQuery {
                if (!isMember(actingUserId, workspaceId)) return@dbReadQuery null
                workspaceAnalytics(workspaceId, range)
            }
            if (result == null) call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
            else call.respond(HttpStatusCode.OK, result)
        } catch (e: Exception) {
            val failure = classifyFailure(e, logger, "Workspace analytics")
            call.respond(failure.status, failure.message)
        }
    }
}

/** Must run inside a transaction. */
internal fun workspaceAnalytics(
    workspaceId: Int,
    range: String,
    now: Long = System.currentTimeMillis()
): WorkspaceAnalyticsResponse {
    val since = ANALYTICS_RANGES.getValue(range)?.let { days -> now - days * DAY_MS }
    val identities = WorkspaceGitHubIdentities.forWorkspace(workspaceId)
    val tally = HashMap<Int, MutableCounts>()
    fun of(userId: Int) = tally.getOrPut(userId) { MutableCounts() }

    // Rows created before created_at existed fall back to their last update.
    fun createdSince(createdAt: Column<Long?>, updatedAt: Column<Long>): Op<Boolean> =
        if (since == null) Op.TRUE else Coalesce(createdAt, updatedAt) greaterEq since

    // ── Tasks ──
    val taskCount = TasksTable.id.count()
    TasksTable.select(TasksTable.createdByUserId, taskCount)
        .where {
            (TasksTable.workspaceId eq workspaceId) and (TasksTable.isDeleted eq false) and
                createdSince(TasksTable.createdAt, TasksTable.updatedAt)
        }
        .groupBy(TasksTable.createdByUserId)
        .forEach { of(it[TasksTable.createdByUserId]).tasksCreated += it[taskCount].toInt() }
    TasksTable.select(TasksTable.assignedToUserId, taskCount)
        .where {
            (TasksTable.workspaceId eq workspaceId) and (TasksTable.isDeleted eq false) and
                (TasksTable.status eq DONE_STATUS) and TasksTable.assignedToUserId.isNotNull() and
                (if (since == null) Op.TRUE else TasksTable.completedAt greaterEq since)
        }
        .groupBy(TasksTable.assignedToUserId)
        .forEach { row -> row[TasksTable.assignedToUserId]?.let { of(it).tasksCompleted += row[taskCount].toInt() } }
    TasksTable.select(TasksTable.assignedToUserId, taskCount)
        .where {
            (TasksTable.workspaceId eq workspaceId) and (TasksTable.isDeleted eq false) and
                (TasksTable.status neq DONE_STATUS) and TasksTable.assignedToUserId.isNotNull()
        }
        .groupBy(TasksTable.assignedToUserId)
        .forEach { row -> row[TasksTable.assignedToUserId]?.let { of(it).tasksOpen += row[taskCount].toInt() } }

    // ── Notes ──
    val noteCount = NotesTable.id.count()
    NotesTable.select(NotesTable.userIdNotes, noteCount)
        .where {
            (NotesTable.workspaceId eq workspaceId) and (NotesTable.isDeleted eq false) and
                createdSince(NotesTable.createdAt, NotesTable.updatedAt)
        }
        .groupBy(NotesTable.userIdNotes)
        .forEach { of(it[NotesTable.userIdNotes]).notesWritten += it[noteCount].toInt() }

    // ── Files ──
    val fileCount = LocalFilesTable.id.count()
    val fileBytes = LocalFilesTable.sizeBytes.sum()
    LocalFilesTable.select(LocalFilesTable.userId, fileCount, fileBytes)
        .where {
            (LocalFilesTable.workspaceId eq workspaceId) and (LocalFilesTable.isDeleted eq false) and
                createdSince(LocalFilesTable.createdAt, LocalFilesTable.updatedAt)
        }
        .groupBy(LocalFilesTable.userId)
        .forEach {
            val counts = of(it[LocalFilesTable.userId])
            counts.filesUploaded += it[fileCount].toInt()
            counts.fileBytes += it[fileBytes] ?: 0L
        }

    // ── GitHub (every repository linked to the workspace) ──
    val repoIds = GitHubRepositoriesTable.select(GitHubRepositoriesTable.id)
        .where { GitHubRepositoriesTable.workspaceId eq workspaceId }
        .map { it[GitHubRepositoriesTable.id] }
    val unlinked = LinkedHashMap<String, MutableUnlinked>()
    fun unlinkedOf(key: String, name: String) = unlinked.getOrPut(key) { MutableUnlinked(name) }

    if (repoIds.isNotEmpty()) {
        val commitCount = GitHubCommitsTable.id.count()
        val authorName = GitHubCommitsTable.authorName.max()
        GitHubCommitsTable
            .select(GitHubCommitsTable.authorGithubId, GitHubCommitsTable.authorLogin, GitHubCommitsTable.authorEmail, authorName, commitCount)
            .where {
                (GitHubCommitsTable.repositoryId inList repoIds) and
                    (if (since == null) Op.TRUE else GitHubCommitsTable.commitDate greaterEq since)
            }
            .groupBy(GitHubCommitsTable.authorGithubId, GitHubCommitsTable.authorLogin, GitHubCommitsTable.authorEmail)
            .forEach {
                val githubId = it[GitHubCommitsTable.authorGithubId]
                val login = it[GitHubCommitsTable.authorLogin]
                val email = it[GitHubCommitsTable.authorEmail]
                val n = it[commitCount].toInt()
                val member = identities.resolve(githubId, login, email)
                if (member != null) {
                    of(member.userId).commits += n
                } else {
                    val name = login ?: it[authorName] ?: email ?: "Unknown"
                    unlinkedOf(githubAuthorKey(login ?: name), name).commits += n
                }
            }

        val prCount = GitHubPullRequestsTable.id.count()
        fun pullRequests(extra: Op<Boolean>, add: (MutableCountsLike, Int) -> Unit) {
            GitHubPullRequestsTable
                .select(GitHubPullRequestsTable.authorGithubId, GitHubPullRequestsTable.authorUsername, prCount)
                .where { (GitHubPullRequestsTable.repositoryId inList repoIds) and extra }
                .groupBy(GitHubPullRequestsTable.authorGithubId, GitHubPullRequestsTable.authorUsername)
                .forEach {
                    val githubId = it[GitHubPullRequestsTable.authorGithubId]
                    val login = it[GitHubPullRequestsTable.authorUsername]
                    val n = it[prCount].toInt()
                    val member = identities.resolve(githubId, login)
                    if (member != null) add(of(member.userId), n)
                    else if (login.isNotBlank()) add(unlinkedOf(githubAuthorKey(login), login), n)
                }
        }
        pullRequests(if (since == null) Op.TRUE else GitHubPullRequestsTable.createdAt greaterEq since) { c, n -> c.pullRequestsOpened += n }
        pullRequests(
            if (since == null) GitHubPullRequestsTable.mergedAt.isNotNull()
            else GitHubPullRequestsTable.mergedAt greaterEq since
        ) { c, n -> c.pullRequestsMerged += n }

        val issueCount = GitHubIssuesTable.id.count()
        GitHubIssuesTable
            .select(GitHubIssuesTable.authorGithubId, GitHubIssuesTable.authorUsername, issueCount)
            .where {
                (GitHubIssuesTable.repositoryId inList repoIds) and
                    (if (since == null) Op.TRUE else GitHubIssuesTable.createdAt greaterEq since)
            }
            .groupBy(GitHubIssuesTable.authorGithubId, GitHubIssuesTable.authorUsername)
            .forEach {
                val githubId = it[GitHubIssuesTable.authorGithubId]
                val login = it[GitHubIssuesTable.authorUsername]
                val n = it[issueCount].toInt()
                val member = identities.resolve(githubId, login)
                if (member != null) of(member.userId).issuesOpened += n
                else if (login.isNotBlank()) unlinkedOf(githubAuthorKey(login), login).issuesOpened += n
            }
    }

    // Every current member is listed, including those with nothing yet. Activity by people who have
    // left the workspace is neither listed nor included in the totals.
    val members = identities.members().map { member ->
        val counts = tally[member.userId]?.toCounts() ?: ContributionCounts()
        MemberContribution(
            userId = member.userId,
            username = member.username,
            avatarUrl = member.avatarUrl,
            githubLogin = identities.loginOf(member.userId),
            counts = counts,
            total = counts.total
        )
    }.sortedWith(compareByDescending<MemberContribution> { it.total }.thenBy { it.username.lowercase() })

    val totals = members.fold(MutableCounts()) { acc, m -> acc.apply { add(m.counts) } }.toCounts()
    return WorkspaceAnalyticsResponse(
        range = range,
        since = since,
        generatedAt = now,
        hasGitHub = repoIds.isNotEmpty(),
        totals = totals,
        members = members,
        unlinkedGitHub = unlinked.values
            .map { it.toContributor() }
            .sortedByDescending { it.total }
            .take(MAX_UNLINKED)
    )
}

private const val DONE_STATUS = "DONE"

/** Unlinked authors are grouped by login (or git name), case-insensitively. */
private fun githubAuthorKey(loginOrName: String): String = loginOrName.lowercase()

private interface MutableCountsLike {
    var pullRequestsOpened: Int
    var pullRequestsMerged: Int
}

private class MutableCounts : MutableCountsLike {
    var tasksCreated = 0
    var tasksCompleted = 0
    var tasksOpen = 0
    var notesWritten = 0
    var filesUploaded = 0
    var fileBytes = 0L
    var commits = 0
    override var pullRequestsOpened = 0
    override var pullRequestsMerged = 0
    var issuesOpened = 0

    fun add(c: ContributionCounts) {
        tasksCreated += c.tasksCreated; tasksCompleted += c.tasksCompleted; tasksOpen += c.tasksOpen
        notesWritten += c.notesWritten; filesUploaded += c.filesUploaded; fileBytes += c.fileBytes
        commits += c.commits; pullRequestsOpened += c.pullRequestsOpened
        pullRequestsMerged += c.pullRequestsMerged; issuesOpened += c.issuesOpened
    }

    fun toCounts() = ContributionCounts(
        tasksCreated = tasksCreated, tasksCompleted = tasksCompleted, tasksOpen = tasksOpen,
        notesWritten = notesWritten, filesUploaded = filesUploaded, fileBytes = fileBytes,
        commits = commits, pullRequestsOpened = pullRequestsOpened,
        pullRequestsMerged = pullRequestsMerged, issuesOpened = issuesOpened
    )
}

private class MutableUnlinked(val name: String) : MutableCountsLike {
    var commits = 0
    override var pullRequestsOpened = 0
    override var pullRequestsMerged = 0
    var issuesOpened = 0

    fun toContributor() = UnlinkedGitHubContributor(
        name = name,
        commits = commits,
        pullRequestsOpened = pullRequestsOpened,
        pullRequestsMerged = pullRequestsMerged,
        issuesOpened = issuesOpened,
        total = commits + pullRequestsOpened + pullRequestsMerged + issuesOpened
    )
}
