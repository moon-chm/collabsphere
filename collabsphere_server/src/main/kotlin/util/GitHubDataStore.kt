package com.collabsphere.util

import com.collabsphere.model.GitHubCommitsTable
import com.collabsphere.model.GitHubCheckSuitesTable
import com.collabsphere.model.GitHubIssuesTable
import com.collabsphere.model.GitHubPullRequestsTable
import com.collabsphere.model.GitHubRepositoriesTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.util.concurrent.ConcurrentHashMap

data class CommitRecord(
    val sha: String,
    val message: String,
    val authorName: String?,
    val authorEmail: String?,
    val commitDate: Long
)

data class PullRequestRecord(
    val githubPrId: Long,
    val number: Int,
    val title: String,
    val state: String,
    val authorUsername: String,
    val createdAt: Long,
    val closedAt: Long?,
    val mergedAt: Long?,
    val body: String = "",
    val url: String? = null,
    val headSha: String? = null
)

data class IssueRecord(
    val githubIssueId: Long,
    val number: Int,
    val title: String,
    val state: String,
    val authorUsername: String,
    val url: String,
    val createdAt: Long,
    val closedAt: Long?,
    val body: String = ""
)

fun GitHubIssueInfo.toRecord(now: Long = System.currentTimeMillis()): IssueRecord =
    IssueRecord(
        githubIssueId = id,
        number = number,
        title = title,
        state = state,
        authorUsername = user?.login ?: "",
        url = html_url,
        createdAt = parseGitHubTime(created_at) ?: now,
        closedAt = parseGitHubTime(closed_at),
        body = body.orEmpty()
    )

object GitHubDataStore {

    fun saveCommits(repositoryId: Int, commits: List<CommitRecord>) {
        if (commits.isEmpty()) return
        GitHubCommitsTable.batchInsert(commits, ignore = true, shouldReturnGeneratedValues = false) { c ->
            this[GitHubCommitsTable.repositoryId] = repositoryId
            this[GitHubCommitsTable.sha] = c.sha
            this[GitHubCommitsTable.message] = c.message
            this[GitHubCommitsTable.authorName] = c.authorName?.take(255)
            this[GitHubCommitsTable.authorEmail] = c.authorEmail?.take(255)
            this[GitHubCommitsTable.commitDate] = c.commitDate
        }
    }

    fun savePullRequest(repositoryId: Int, pr: PullRequestRecord) {
        GitHubPullRequestsTable.upsert(GitHubPullRequestsTable.repositoryId, GitHubPullRequestsTable.githubPrId) {
            it[GitHubPullRequestsTable.repositoryId] = repositoryId
            it[GitHubPullRequestsTable.githubPrId] = pr.githubPrId
            it[GitHubPullRequestsTable.number] = pr.number
            it[GitHubPullRequestsTable.title] = pr.title.take(500)
            it[GitHubPullRequestsTable.state] = pr.state
            it[GitHubPullRequestsTable.authorUsername] = pr.authorUsername.take(255)
            it[GitHubPullRequestsTable.createdAt] = pr.createdAt
            it[GitHubPullRequestsTable.closedAt] = pr.closedAt
            it[GitHubPullRequestsTable.mergedAt] = pr.mergedAt
            it[GitHubPullRequestsTable.headSha] = pr.headSha
        }
    }

    fun saveIssue(repositoryId: Int, issue: IssueRecord) {
        GitHubIssuesTable.upsert(GitHubIssuesTable.repositoryId, GitHubIssuesTable.githubIssueId) {
            it[GitHubIssuesTable.repositoryId] = repositoryId
            it[GitHubIssuesTable.githubIssueId] = issue.githubIssueId
            it[GitHubIssuesTable.number] = issue.number
            it[GitHubIssuesTable.title] = issue.title.take(500)
            it[GitHubIssuesTable.state] = issue.state
            it[GitHubIssuesTable.authorUsername] = issue.authorUsername.take(255)
            it[GitHubIssuesTable.url] = issue.url.take(500)
            it[GitHubIssuesTable.createdAt] = issue.createdAt
            it[GitHubIssuesTable.closedAt] = issue.closedAt
        }
    }

    fun saveCheckSuite(repositoryId: Int, suiteId: Long, headSha: String, status: String, conclusion: String?) {
        GitHubCheckSuitesTable.upsert(GitHubCheckSuitesTable.repositoryId, GitHubCheckSuitesTable.githubSuiteId) {
            it[GitHubCheckSuitesTable.repositoryId] = repositoryId
            it[GitHubCheckSuitesTable.githubSuiteId] = suiteId
            it[GitHubCheckSuitesTable.headSha] = headSha.take(40)
            it[GitHubCheckSuitesTable.status] = status.take(20)
            it[GitHubCheckSuitesTable.conclusion] = conclusion?.take(30)
            it[GitHubCheckSuitesTable.updatedAt] = System.currentTimeMillis()
        }
    }

    fun ciStatusFor(repositoryId: Int, shas: Collection<String>): Map<String, String> {
        if (shas.isEmpty()) return emptyMap()
        return GitHubCheckSuitesTable.selectAll()
            .where { (GitHubCheckSuitesTable.repositoryId eq repositoryId) and (GitHubCheckSuitesTable.headSha inList shas.toList()) }
            .groupBy({ it[GitHubCheckSuitesTable.headSha] }) {
                it[GitHubCheckSuitesTable.status] to it[GitHubCheckSuitesTable.conclusion]
            }
            .mapValues { (_, suites) -> overallCiStatus(suites) }
    }

    private fun overallCiStatus(suites: List<Pair<String, String?>>): String = when {
        suites.any { (status, _) -> status != "completed" } -> CI_PENDING
        suites.any { (_, conclusion) -> conclusion in FAILING_CONCLUSIONS } -> CI_FAILURE
        else -> CI_SUCCESS
    }

    const val CI_PENDING = "pending"
    const val CI_FAILURE = "failure"
    const val CI_SUCCESS = "success"
    private val FAILING_CONCLUSIONS = setOf("failure", "timed_out", "cancelled", "action_required", "startup_failure")

    fun markSynced(repositoryId: Int) {
        GitHubRepositoriesTable.update({ GitHubRepositoriesTable.id eq repositoryId }) {
            it[GitHubRepositoriesTable.lastSyncedAt] = System.currentTimeMillis()
        }
    }

    private const val SYNC_OVERLAP_MS = 24 * 60 * 60 * 1000L

    suspend fun sync(repositoryId: Int, token: String, repoFullName: String, branch: String): List<GitHubActivity> {
        val latestCommitDate = newSuspendedTransaction(Dispatchers.IO) {
            val maxDate = GitHubCommitsTable.commitDate.max()
            GitHubCommitsTable.select(maxDate)
                .where { GitHubCommitsTable.repositoryId eq repositoryId }
                .singleOrNull()
                ?.get(maxDate)
        }
        val since = latestCommitDate?.minus(SYNC_OVERLAP_MS)
        val incremental = since != null

        val now = System.currentTimeMillis()
        val commits = GitHubService.getCommits(token, repoFullName, branch, since).orEmpty().map { c ->
            CommitRecord(
                sha = c.sha,
                message = c.commit?.message ?: "",
                authorName = c.commit?.author?.name,
                authorEmail = c.commit?.author?.email,
                commitDate = parseGitHubTime(c.commit?.author?.date) ?: now
            )
        }
        val issues = GitHubService.getIssues(token, repoFullName, incremental).orEmpty().map { it.toRecord(now) }
        val pullRequests = GitHubService.getPullRequests(token, repoFullName, incremental).orEmpty().map { pr ->
            PullRequestRecord(
                githubPrId = pr.id,
                number = pr.number,
                title = pr.title,
                state = pr.state,
                authorUsername = pr.user?.login ?: "",
                createdAt = parseGitHubTime(pr.created_at) ?: now,
                closedAt = parseGitHubTime(pr.closed_at),
                mergedAt = parseGitHubTime(pr.merged_at),
                body = pr.body.orEmpty(),
                url = pr.html_url,
                headSha = pr.head?.sha
            )
        }

        return newSuspendedTransaction(Dispatchers.IO) {
            val stillLinked = GitHubRepositoriesTable.selectAll()
                .where { GitHubRepositoriesTable.id eq repositoryId }
                .count() > 0
            if (!stillLinked) return@newSuspendedTransaction emptyList()

            val activities = if (incremental) {
                missedActivities(repositoryId, branch, commits, pullRequests, issues)
            } else {
                emptyList()
            }

            commits.chunked(500).forEach { saveCommits(repositoryId, it) }
            pullRequests.forEach { savePullRequest(repositoryId, it) }
            issues.forEach { saveIssue(repositoryId, it) }
            markSynced(repositoryId)
            activities
        }
    }

    private fun missedActivities(
        repositoryId: Int,
        branch: String,
        commits: List<CommitRecord>,
        pullRequests: List<PullRequestRecord>,
        issues: List<IssueRecord>
    ): List<GitHubActivity> {
        val activities = mutableListOf<GitHubActivity>()

        val knownShas = commits.map { it.sha }.chunked(500).flatMap { shas ->
            GitHubCommitsTable.select(GitHubCommitsTable.sha)
                .where { (GitHubCommitsTable.repositoryId eq repositoryId) and (GitHubCommitsTable.sha inList shas) }
                .map { it[GitHubCommitsTable.sha] }
        }.toSet()
        val newCommits = commits.filter { it.sha !in knownShas }
        if (newCommits.isNotEmpty()) {
            activities += GitHubPushActivity(repositoryId, pusher = "", branch = branch, commits = newCommits, announce = false)
        }

        val knownMerged = pullRequests.map { it.githubPrId }.chunked(500).flatMap { ids ->
            GitHubPullRequestsTable.selectAll()
                .where { (GitHubPullRequestsTable.repositoryId eq repositoryId) and (GitHubPullRequestsTable.githubPrId inList ids) }
                .map { it[GitHubPullRequestsTable.githubPrId] to (it[GitHubPullRequestsTable.mergedAt] != null) }
        }.toMap()
        pullRequests.forEach { pr ->
            val wasMerged = knownMerged[pr.githubPrId]
            val action = when {
                pr.mergedAt != null && wasMerged != true -> "closed"
                wasMerged == null && pr.state == "open" -> "opened"
                else -> return@forEach
            }
            activities += GitHubPullRequestActivity(repositoryId, action, pr, pr.url, announce = false)
        }

        val knownIssueStates = issues.map { it.githubIssueId }.chunked(500).flatMap { ids ->
            GitHubIssuesTable.selectAll()
                .where { (GitHubIssuesTable.repositoryId eq repositoryId) and (GitHubIssuesTable.githubIssueId inList ids) }
                .map { it[GitHubIssuesTable.githubIssueId] to it[GitHubIssuesTable.state] }
        }.toMap()
        issues.forEach { issue ->
            val previousState = knownIssueStates[issue.githubIssueId]
            val action = when {
                issue.state == "closed" && previousState == "open" -> "closed"
                previousState == null && issue.state == "open" -> "opened"
                else -> return@forEach
            }
            activities += GitHubIssueActivity(repositoryId, action, issue, announce = false)
        }
        return activities
    }
}

object GitHubSyncManager {

    private const val MANUAL_SYNC_COOLDOWN_MS = 30_000L
    private const val AUTO_SYNC_COOLDOWN_MS = 5 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap.newKeySet<Int>()
    private val lastManualSync = ConcurrentHashMap<Int, Long>()
    private val lastAutoSync = ConcurrentHashMap<Int, Long>()

    @Volatile
    var activityHandler: (suspend (List<GitHubActivity>) -> Unit)? = null

    fun isSyncing(repositoryId: Int): Boolean = repositoryId in running

    private fun cooledDown(map: ConcurrentHashMap<Int, Long>, repositoryId: Int, cooldownMs: Long): Boolean =
        System.currentTimeMillis() - (map[repositoryId] ?: 0L) >= cooldownMs

    fun start(
        repositoryId: Int,
        installationId: Long,
        userToken: String?,
        repoFullName: String,
        branch: String,
        trigger: SyncTrigger = SyncTrigger.LINK
    ): Boolean {
        val cooldownMap = when (trigger) {
            SyncTrigger.MANUAL -> lastManualSync
            SyncTrigger.AUTO -> lastAutoSync
            SyncTrigger.LINK -> null
        }
        val cooldownMs = if (trigger == SyncTrigger.MANUAL) MANUAL_SYNC_COOLDOWN_MS else AUTO_SYNC_COOLDOWN_MS
        if (cooldownMap != null && !cooledDown(cooldownMap, repositoryId, cooldownMs)) return false
        if (!running.add(repositoryId)) return false
        cooldownMap?.put(repositoryId, System.currentTimeMillis())
        scope.launch {
            try {
                val token = GitHubService.repoAccessToken(installationId, userToken)
                if (token == null) {
                    println("[GitHub] No token available to sync $repoFullName")
                    return@launch
                }
                val activities = GitHubDataStore.sync(repositoryId, token, repoFullName, branch)
                if (activities.isNotEmpty()) activityHandler?.invoke(activities)
            } catch (e: Exception) {
                println("[GitHub] Sync failed for $repoFullName: ${e.message}")
            } finally {
                running.remove(repositoryId)
            }
        }
        return true
    }
}

enum class SyncTrigger { LINK, MANUAL, AUTO }
