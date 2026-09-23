package plugins

import com.collabsphere.model.ChannelsTable
import com.collabsphere.model.GitHubCommitsTable
import com.collabsphere.model.GitHubIssuesTable
import com.collabsphere.model.GitHubPullRequestsTable
import io.ktor.server.application.Application
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.count
import com.collabsphere.model.GitHubRepositoriesTable
import com.collabsphere.model.GitHubTaskLinksTable
import com.collabsphere.model.MessageTable
import com.collabsphere.model.TasksTable
import com.collabsphere.util.GitHubActivity
import com.collabsphere.model.GitHubConnectionsTable
import com.collabsphere.util.GitHubBot
import com.collabsphere.util.GitHubIssueActivity
import com.collabsphere.util.GitHubService
import com.collabsphere.util.GitHubPullRequestActivity
import com.collabsphere.util.GitHubPushActivity
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

private val TASK_REFERENCE = Regex("""(?i)(?<![\w-])T-(\d+)\b""")
private val CLOSING_REFERENCE = Regex("""(?i)\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s*:?\s+T-(\d+)\b""")

private const val STATUS_TO_DO = "TO_DO"
private const val STATUS_IN_PROGRESS = "IN_PROGRESS"
private const val STATUS_DONE = "DONE"
private const val MAX_COMMITS_IN_POST = 5
private const val DIGEST_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L
private const val DIGEST_CHECK_INTERVAL_MS = 60 * 60 * 1000L
private const val DIGEST_STARTUP_DELAY_MS = 2 * 60 * 1000L
private const val DIGEST_TOP_CONTRIBUTORS = 3

private data class LinkedRepo(
    val id: Int,
    val workspaceId: Int,
    val fullName: String,
    val htmlUrl: String,
    val notifyChannelId: Int?
)

private data class TaskChange(
    val taskId: Int,
    val workspaceId: Int,
    val taskName: String,
    val assigneeId: Int?,
    val status: String,
    val source: String
)

internal fun taskReferences(text: String): Set<Int> =
    TASK_REFERENCE.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()

internal fun closingTaskReferences(text: String): Set<Int> =
    CLOSING_REFERENCE.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()

fun Application.startGitHubDigestScheduler() {
    launch {
        delay(DIGEST_STARTUP_DELAY_MS)
        while (isActive) {
            try {
                postDueDigests(System.currentTimeMillis())
            } catch (e: Exception) {
                println("[GitHub] Weekly digest run failed: ${e.message}")
            }
            delay(DIGEST_CHECK_INTERVAL_MS)
        }
    }
}

private suspend fun postDueDigests(now: Long) {
    val candidates = dbQuery {
        GitHubRepositoriesTable.selectAll()
            .where { GitHubRepositoriesTable.notifyChannelId.isNotNull() }
            .map { it.toLinkedRepo() to it[GitHubRepositoriesTable.lastDigestAt] }
    }
    for ((repo, lastDigestAt) in candidates) {
        if (lastDigestAt != null && now - lastDigestAt < DIGEST_INTERVAL_MS) continue
        if (lastDigestAt != null) {
            val digest = dbQuery { buildWeeklyDigest(repo, now - DIGEST_INTERVAL_MS) }
            digest?.let { postToChannel(repo, it) }
        }
        dbQuery {
            GitHubRepositoriesTable.update({ GitHubRepositoriesTable.id eq repo.id }) {
                it[GitHubRepositoriesTable.lastDigestAt] = now
            }
        }
    }
}

internal fun formatWeeklyDigest(
    repoFullName: String,
    commits: Int,
    pullRequestsOpened: Int,
    pullRequestsMerged: Int,
    issuesOpened: Int,
    issuesClosed: Int,
    topContributors: List<Pair<String, Int>>
): String? {
    if (commits + pullRequestsOpened + pullRequestsMerged + issuesOpened + issuesClosed == 0) return null
    val lines = mutableListOf("Weekly GitHub digest for $repoFullName")
    lines += "• $commits ${if (commits == 1) "commit" else "commits"}"
    lines += "• $pullRequestsMerged PRs merged, $pullRequestsOpened opened"
    lines += "• $issuesOpened issues opened, $issuesClosed closed"
    if (topContributors.isNotEmpty()) {
        lines += "Top contributors: " + topContributors.joinToString(", ") { (name, count) -> "$name ($count)" }
    }
    return lines.joinToString("\n")
}

private fun buildWeeklyDigest(repo: LinkedRepo, since: Long): String? {
    val commits = GitHubCommitsTable.selectAll()
        .where { (GitHubCommitsTable.repositoryId eq repo.id) and (GitHubCommitsTable.commitDate greaterEq since) }
        .count().toInt()
    val pullRequestsOpened = GitHubPullRequestsTable.selectAll()
        .where { (GitHubPullRequestsTable.repositoryId eq repo.id) and (GitHubPullRequestsTable.createdAt greaterEq since) }
        .count().toInt()
    val pullRequestsMerged = GitHubPullRequestsTable.selectAll()
        .where { (GitHubPullRequestsTable.repositoryId eq repo.id) and (GitHubPullRequestsTable.mergedAt greaterEq since) }
        .count().toInt()
    val issuesOpened = GitHubIssuesTable.selectAll()
        .where { (GitHubIssuesTable.repositoryId eq repo.id) and (GitHubIssuesTable.createdAt greaterEq since) }
        .count().toInt()
    val issuesClosed = GitHubIssuesTable.selectAll()
        .where { (GitHubIssuesTable.repositoryId eq repo.id) and (GitHubIssuesTable.closedAt greaterEq since) }
        .count().toInt()

    val commitCount = GitHubCommitsTable.id.count()
    val topContributors = GitHubCommitsTable
        .select(GitHubCommitsTable.authorName, commitCount)
        .where { (GitHubCommitsTable.repositoryId eq repo.id) and (GitHubCommitsTable.commitDate greaterEq since) }
        .groupBy(GitHubCommitsTable.authorName)
        .orderBy(commitCount, SortOrder.DESC)
        .limit(DIGEST_TOP_CONTRIBUTORS)
        .map { (it[GitHubCommitsTable.authorName] ?: "Unknown") to it[commitCount].toInt() }

    return formatWeeklyDigest(
        repoFullName = repo.fullName,
        commits = commits,
        pullRequestsOpened = pullRequestsOpened,
        pullRequestsMerged = pullRequestsMerged,
        issuesOpened = issuesOpened,
        issuesClosed = issuesClosed,
        topContributors = topContributors
    )
}

private fun ResultRow.toLinkedRepo() = LinkedRepo(
    id = this[GitHubRepositoriesTable.id],
    workspaceId = this[GitHubRepositoriesTable.workspaceId],
    fullName = this[GitHubRepositoriesTable.fullName],
    htmlUrl = this[GitHubRepositoriesTable.htmlUrl],
    notifyChannelId = this[GitHubRepositoriesTable.notifyChannelId]
)

suspend fun processGitHubActivities(activities: List<GitHubActivity>) {
    for (activity in activities) {
        try {
            val repo = dbQuery {
                GitHubRepositoriesTable.selectAll()
                    .where { GitHubRepositoriesTable.id eq activity.repositoryId }
                    .singleOrNull()
                    ?.toLinkedRepo()
            } ?: continue

            when (activity) {
                is GitHubPushActivity -> handlePush(repo, activity)
                is GitHubPullRequestActivity -> handlePullRequest(repo, activity)
                is GitHubIssueActivity -> handleIssue(repo, activity)
            }
        } catch (e: Exception) {
            println("[GitHub] Automation failed for repository ${activity.repositoryId}: ${e.message}")
        }
    }
}

private suspend fun handlePush(repo: LinkedRepo, activity: GitHubPushActivity) {
    val changes = activity.commits.flatMap { commit ->
        val shortSha = commit.sha.take(7)
        linkTasks(
            repo = repo,
            kind = "commit",
            ref = commit.sha,
            title = commit.message.lineSequence().first(),
            url = "${repo.htmlUrl}/commit/${commit.sha}",
            referenced = taskReferences(commit.message),
            closing = closingTaskReferences(commit.message),
            openStatus = null,
            source = "commit $shortSha"
        )
    }
    notifyAssignees(changes)

    val count = activity.commits.size
    val lines = activity.commits.takeLast(MAX_COMMITS_IN_POST).joinToString("\n") {
        "• ${it.message.lineSequence().first().take(80)} (${it.sha.take(7)})"
    }
    val more = if (count > MAX_COMMITS_IN_POST) "\n…and ${count - MAX_COMMITS_IN_POST} more" else ""
    val noun = if (count == 1) "commit" else "commits"
    if (!activity.announce) return
    postToChannel(repo, "${activity.pusher} pushed $count $noun to ${repo.fullName} (${activity.branch})\n$lines$more")
}

private suspend fun handlePullRequest(repo: LinkedRepo, activity: GitHubPullRequestActivity) {
    val pr = activity.pullRequest
    val text = "${pr.title}\n${pr.body}"
    val url = activity.url ?: "${repo.htmlUrl}/pull/${pr.number}"
    val author = pr.authorUsername.ifBlank { "Someone" }
    val merged = activity.action == "closed" && pr.mergedAt != null

    val post: String?
    val closing: Set<Int>
    val openStatus: String?
    when {
        activity.action == "opened" || activity.action == "reopened" -> {
            post = "$author opened PR #${pr.number}: ${pr.title}"
            closing = emptySet()
            openStatus = STATUS_IN_PROGRESS
        }
        merged -> {
            post = "PR #${pr.number} merged: ${pr.title}"
            closing = closingTaskReferences(text)
            openStatus = null
        }
        activity.action == "closed" -> {
            post = "PR #${pr.number} closed without merging: ${pr.title}"
            closing = emptySet()
            openStatus = null
        }
        activity.action == "edited" -> {
            post = null
            closing = emptySet()
            openStatus = null
        }
        else -> return
    }

    val changes = linkTasks(
        repo = repo,
        kind = "pr",
        ref = pr.number.toString(),
        title = "#${pr.number} ${pr.title}",
        url = url,
        referenced = taskReferences(text),
        closing = closing,
        openStatus = openStatus,
        source = "PR #${pr.number}"
    )
    notifyAssignees(changes)
    if (activity.announce) post?.let { postToChannel(repo, "$it\n$url") }
}

private suspend fun handleIssue(repo: LinkedRepo, activity: GitHubIssueActivity) {
    val issue = activity.issue
    val text = "${issue.title}\n${issue.body}"
    val author = issue.authorUsername.ifBlank { "Someone" }

    val post: String?
    val closing: Set<Int>
    when (activity.action) {
        "opened", "reopened" -> {
            post = "$author ${activity.action} issue #${issue.number}: ${issue.title}"
            closing = emptySet()
        }
        "closed" -> {
            post = "Issue #${issue.number} closed: ${issue.title}"
            closing = tasksLinkedToIssue(repo.id, issue.number) + closingTaskReferences(text)
        }
        "edited" -> {
            post = null
            closing = emptySet()
        }
        else -> return
    }

    val changes = linkTasks(
        repo = repo,
        kind = "issue",
        ref = issue.number.toString(),
        title = "#${issue.number} ${issue.title}",
        url = issue.url,
        referenced = taskReferences(text),
        closing = closing,
        openStatus = null,
        source = "issue #${issue.number}"
    )
    notifyAssignees(changes)
    if (activity.announce) post?.let { postToChannel(repo, "$it\n${issue.url}") }
}

private suspend fun tasksLinkedToIssue(repositoryId: Int, number: Int): Set<Int> = dbQuery {
    GitHubTaskLinksTable.selectAll()
        .where {
            (GitHubTaskLinksTable.repositoryId eq repositoryId) and
                (GitHubTaskLinksTable.kind eq "issue") and
                (GitHubTaskLinksTable.ref eq number.toString())
        }
        .map { it[GitHubTaskLinksTable.taskId] }
        .toSet()
}

suspend fun syncLinkedIssuesWithTask(taskId: Int, previousStatus: String?, newStatus: String) {
    val wasDone = previousStatus == STATUS_DONE
    val isDone = newStatus == STATUS_DONE
    if (wasDone == isDone) return
    val desiredState = if (isDone) "closed" else "open"

    val targets = dbQuery {
        GitHubTaskLinksTable.selectAll()
            .where { (GitHubTaskLinksTable.taskId eq taskId) and (GitHubTaskLinksTable.kind eq "issue") }
            .mapNotNull { link ->
                val repoRow = GitHubRepositoriesTable.selectAll()
                    .where { GitHubRepositoriesTable.id eq link[GitHubTaskLinksTable.repositoryId] }
                    .singleOrNull() ?: return@mapNotNull null
                val connRow = GitHubConnectionsTable.selectAll()
                    .where { GitHubConnectionsTable.id eq repoRow[GitHubRepositoriesTable.connectionId] }
                    .singleOrNull() ?: return@mapNotNull null
                IssueTarget(
                    number = link[GitHubTaskLinksTable.ref].toIntOrNull() ?: return@mapNotNull null,
                    repoFullName = repoRow[GitHubRepositoriesTable.fullName],
                    installationId = connRow[GitHubConnectionsTable.installationId],
                    userToken = connRow[GitHubConnectionsTable.accessTokenEncrypted]
                )
            }
    }

    targets.forEach { target ->
        val token = GitHubService.repoAccessToken(target.installationId, target.userToken) ?: return@forEach
        GitHubService.setIssueState(token, target.repoFullName, target.number, desiredState)
    }
}

private data class IssueTarget(
    val number: Int,
    val repoFullName: String,
    val installationId: Long,
    val userToken: String?
)

private suspend fun linkTasks(
    repo: LinkedRepo,
    kind: String,
    ref: String,
    title: String,
    url: String,
    referenced: Set<Int>,
    closing: Set<Int>,
    openStatus: String?,
    source: String
): List<TaskChange> {
    val taskIds = referenced + closing
    if (taskIds.isEmpty()) return emptyList()

    return dbQuery {
        val now = System.currentTimeMillis()
        TasksTable.selectAll()
            .where {
                (TasksTable.id inList taskIds) and
                    (TasksTable.workspaceId eq repo.workspaceId) and
                    (TasksTable.isDeleted eq false)
            }
            .toList()
            .mapNotNull { task ->
                val taskId = task[TasksTable.id]
                GitHubTaskLinksTable.insertIgnore {
                    it[GitHubTaskLinksTable.taskId] = taskId
                    it[GitHubTaskLinksTable.repositoryId] = repo.id
                    it[GitHubTaskLinksTable.kind] = kind
                    it[GitHubTaskLinksTable.ref] = ref.take(64)
                    it[GitHubTaskLinksTable.title] = title.take(500)
                    it[GitHubTaskLinksTable.url] = url.take(500)
                }

                val currentStatus = task[TasksTable.status]
                val newStatus = when {
                    taskId in closing && currentStatus != STATUS_DONE -> STATUS_DONE
                    openStatus != null && currentStatus == STATUS_TO_DO -> openStatus
                    else -> return@mapNotNull null
                }
                TasksTable.update({ TasksTable.id eq taskId }) {
                    it[TasksTable.status] = newStatus
                    it[TasksTable.updatedAt] = now
                }
                TaskChange(
                    taskId = taskId,
                    workspaceId = repo.workspaceId,
                    taskName = task[TasksTable.taskName],
                    assigneeId = task[TasksTable.assignedToUserId],
                    status = newStatus,
                    source = source
                )
            }
    }
}

private suspend fun notifyAssignees(changes: List<TaskChange>) {
    changes.forEach { change ->
        val assigneeId = change.assigneeId ?: return@forEach
        createAndPushNotification(
            recipientId = assigneeId,
            actorId = null,
            type = "TASK_UPDATED",
            title = "GitHub updated your task",
            body = "\"${change.taskName}\" is now ${change.status} via ${change.source}",
            workspaceId = change.workspaceId,
            referenceId = change.taskId
        )
    }
}

private suspend fun postToChannel(repo: LinkedRepo, content: String) {
    val channelId = repo.notifyChannelId ?: return
    dbQuery {
        val channelExists = ChannelsTable.selectAll()
            .where {
                (ChannelsTable.id eq channelId) and
                    (ChannelsTable.workspaceId eq repo.workspaceId) and
                    (ChannelsTable.isDeleted eq false)
            }
            .count() > 0
        if (!channelExists) return@dbQuery
        MessageTable.insert {
            it[MessageTable.userId] = GitHubBot.ensureExists()
            it[MessageTable.workspaceId] = repo.workspaceId
            it[MessageTable.channelId] = channelId
            it[MessageTable.userName] = GitHubBot.USERNAME
            it[MessageTable.content] = content.take(4000)
            it[MessageTable.status] = "Delivered"
            it[MessageTable.isDeleted] = false
            it[MessageTable.updatedAt] = System.currentTimeMillis()
        }
    }
}
