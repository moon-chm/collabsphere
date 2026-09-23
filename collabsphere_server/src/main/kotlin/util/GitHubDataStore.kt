package com.collabsphere.util

import com.collabsphere.model.GitHubCommitsTable
import com.collabsphere.model.GitHubPullRequestsTable
import com.collabsphere.model.GitHubRepositoriesTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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
    val body: String = ""
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
        }
    }

    fun markSynced(repositoryId: Int) {
        GitHubRepositoriesTable.update({ GitHubRepositoriesTable.id eq repositoryId }) {
            it[GitHubRepositoriesTable.lastSyncedAt] = System.currentTimeMillis()
        }
    }

    private const val SYNC_OVERLAP_MS = 24 * 60 * 60 * 1000L

    suspend fun sync(repositoryId: Int, token: String, repoFullName: String, branch: String) {
        val latestCommitDate = newSuspendedTransaction(Dispatchers.IO) {
            val maxDate = GitHubCommitsTable.commitDate.max()
            GitHubCommitsTable.select(maxDate)
                .where { GitHubCommitsTable.repositoryId eq repositoryId }
                .singleOrNull()
                ?.get(maxDate)
        }
        val since = latestCommitDate?.minus(SYNC_OVERLAP_MS)

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
        val pullRequests = GitHubService.getPullRequests(token, repoFullName, incremental = since != null).orEmpty().map { pr ->
            PullRequestRecord(
                githubPrId = pr.id,
                number = pr.number,
                title = pr.title,
                state = pr.state,
                authorUsername = pr.user?.login ?: "",
                createdAt = parseGitHubTime(pr.created_at) ?: now,
                closedAt = parseGitHubTime(pr.closed_at),
                mergedAt = parseGitHubTime(pr.merged_at)
            )
        }
        newSuspendedTransaction(Dispatchers.IO) {
            val stillLinked = GitHubRepositoriesTable.selectAll()
                .where { GitHubRepositoriesTable.id eq repositoryId }
                .count() > 0
            if (!stillLinked) return@newSuspendedTransaction
            commits.chunked(500).forEach { saveCommits(repositoryId, it) }
            pullRequests.forEach { savePullRequest(repositoryId, it) }
            markSynced(repositoryId)
        }
    }
}

object GitHubSyncManager {

    private const val MANUAL_SYNC_COOLDOWN_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap.newKeySet<Int>()
    private val lastManualSync = ConcurrentHashMap<Int, Long>()

    fun isSyncing(repositoryId: Int): Boolean = repositoryId in running

    fun canSyncManually(repositoryId: Int): Boolean =
        System.currentTimeMillis() - (lastManualSync[repositoryId] ?: 0L) >= MANUAL_SYNC_COOLDOWN_MS

    fun start(
        repositoryId: Int,
        installationId: Long,
        userToken: String?,
        repoFullName: String,
        branch: String,
        manual: Boolean = false
    ): Boolean {
        if (manual && !canSyncManually(repositoryId)) return false
        if (!running.add(repositoryId)) return false
        if (manual) lastManualSync[repositoryId] = System.currentTimeMillis()
        scope.launch {
            try {
                val token = GitHubService.repoAccessToken(installationId, userToken)
                if (token == null) {
                    println("[GitHub] No token available to sync $repoFullName")
                    return@launch
                }
                GitHubDataStore.sync(repositoryId, token, repoFullName, branch)
            } catch (e: Exception) {
                println("[GitHub] Sync failed for $repoFullName: ${e.message}")
            } finally {
                running.remove(repositoryId)
            }
        }
        return true
    }
}
