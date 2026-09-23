package com.collabsphere.util

import com.collabsphere.model.GitHubConnectionsTable
import com.collabsphere.model.GitHubRepositoriesTable
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

sealed interface GitHubActivity {
    val repositoryId: Int
}

data class GitHubPushActivity(
    override val repositoryId: Int,
    val pusher: String,
    val branch: String,
    val commits: List<CommitRecord>,
    val announce: Boolean = true
) : GitHubActivity

data class GitHubPullRequestActivity(
    override val repositoryId: Int,
    val action: String,
    val pullRequest: PullRequestRecord,
    val url: String?,
    val announce: Boolean = true
) : GitHubActivity

data class GitHubIssueActivity(
    override val repositoryId: Int,
    val action: String,
    val issue: IssueRecord,
    val announce: Boolean = true
) : GitHubActivity

object GitHubWebhookService {

    private val webhookSecret = System.getenv("GITHUB_WEBHOOK_SECRET")
    private val json = Json { ignoreUnknownKeys = true }

    fun verifySignature(payload: ByteArray, signatureHeader: String?): Boolean {
        if (webhookSecret.isNullOrBlank() || signatureHeader == null) return false
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(webhookSecret.toByteArray(), "HmacSHA256"))
            val expected = "sha256=" + mac.doFinal(payload).joinToString("") { "%02x".format(it) }
            MessageDigest.isEqual(expected.toByteArray(), signatureHeader.toByteArray())
        } catch (e: Exception) {
            println("[GitHub] Webhook signature check failed: ${e.message}")
            false
        }
    }

    suspend fun handleWebhookEvent(eventType: String?, payloadJson: String): List<GitHubActivity> {
        val payload = try {
            json.parseToJsonElement(payloadJson).jsonObject
        } catch (e: Exception) {
            println("[GitHub] Failed to parse webhook JSON: ${e.message}")
            return emptyList()
        }

        return newSuspendedTransaction(Dispatchers.IO) {
            when (eventType) {
                "push" -> handlePushEvent(payload)
                "pull_request" -> handlePullRequestEvent(payload)
                "issues" -> handleIssuesEvent(payload)
                "check_suite" -> {
                    handleCheckSuiteEvent(payload)
                    emptyList()
                }
                "installation" -> {
                    handleInstallationEvent(payload)
                    emptyList()
                }
                "installation_repositories" -> {
                    handleInstallationRepositoriesEvent(payload)
                    emptyList()
                }
                else -> emptyList()
            }
        }
    }

    private fun linkedRepositoryIds(githubRepoId: Long): List<Int> =
        GitHubRepositoriesTable.selectAll()
            .where { GitHubRepositoriesTable.githubRepoId eq githubRepoId }
            .map { it[GitHubRepositoriesTable.id] }

    private fun handlePushEvent(payload: JsonObject): List<GitHubActivity> {
        val repo = payload["repository"]?.jsonObject ?: return emptyList()
        val githubRepoId = repo["id"]?.jsonPrimitive?.longOrNull ?: return emptyList()
        val defaultBranch = repo["default_branch"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        if (payload["ref"]?.jsonPrimitive?.contentOrNull != "refs/heads/$defaultBranch") return emptyList()
        val pusher = payload["pusher"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            ?: payload["sender"]?.jsonObject?.get("login")?.jsonPrimitive?.contentOrNull
            ?: "Someone"

        val now = System.currentTimeMillis()
        val commits = payload["commits"]?.jsonArray?.mapNotNull { element ->
            val commit = element.jsonObject
            val sha = commit["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val author = commit["author"]?.jsonObject
            CommitRecord(
                sha = sha,
                message = commit["message"]?.jsonPrimitive?.contentOrNull ?: "",
                authorName = author?.get("name")?.jsonPrimitive?.contentOrNull,
                authorEmail = author?.get("email")?.jsonPrimitive?.contentOrNull,
                commitDate = parseGitHubTime(commit["timestamp"]?.jsonPrimitive?.contentOrNull) ?: now
            )
        } ?: return emptyList()
        if (commits.isEmpty()) return emptyList()

        return linkedRepositoryIds(githubRepoId).map { repositoryId ->
            GitHubDataStore.saveCommits(repositoryId, commits)
            GitHubDataStore.markSynced(repositoryId)
            GitHubPushActivity(repositoryId, pusher, defaultBranch, commits)
        }
    }

    private fun handlePullRequestEvent(payload: JsonObject): List<GitHubActivity> {
        val action = payload["action"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        val pr = payload["pull_request"]?.jsonObject ?: return emptyList()
        val repo = payload["repository"]?.jsonObject ?: return emptyList()
        val githubRepoId = repo["id"]?.jsonPrimitive?.longOrNull ?: return emptyList()

        val record = PullRequestRecord(
            githubPrId = pr["id"]?.jsonPrimitive?.longOrNull ?: return emptyList(),
            number = pr["number"]?.jsonPrimitive?.intOrNull ?: return emptyList(),
            title = pr["title"]?.jsonPrimitive?.contentOrNull ?: "",
            state = pr["state"]?.jsonPrimitive?.contentOrNull ?: "open",
            authorUsername = pr["user"]?.jsonObject?.get("login")?.jsonPrimitive?.contentOrNull ?: "",
            createdAt = parseGitHubTime(pr["created_at"]?.jsonPrimitive?.contentOrNull) ?: System.currentTimeMillis(),
            closedAt = parseGitHubTime(pr["closed_at"]?.jsonPrimitive?.contentOrNull),
            mergedAt = parseGitHubTime(pr["merged_at"]?.jsonPrimitive?.contentOrNull),
            headSha = pr["head"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull
        )

        val url = pr["html_url"]?.jsonPrimitive?.contentOrNull
        val body = pr["body"]?.jsonPrimitive?.contentOrNull.orEmpty()

        return linkedRepositoryIds(githubRepoId).map { repositoryId ->
            GitHubDataStore.savePullRequest(repositoryId, record)
            GitHubDataStore.markSynced(repositoryId)
            GitHubPullRequestActivity(repositoryId, action, record.copy(body = body), url)
        }
    }

    private fun handleIssuesEvent(payload: JsonObject): List<GitHubActivity> {
        val action = payload["action"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        val issueJson = payload["issue"] ?: return emptyList()
        val repo = payload["repository"]?.jsonObject ?: return emptyList()
        val githubRepoId = repo["id"]?.jsonPrimitive?.longOrNull ?: return emptyList()
        val issue = try {
            json.decodeFromJsonElement(GitHubIssueInfo.serializer(), issueJson)
        } catch (e: Exception) {
            println("[GitHub] Failed to parse issue payload: ${e.message}")
            return emptyList()
        }
        if (issue.pull_request != null) return emptyList()
        val record = issue.toRecord()

        return linkedRepositoryIds(githubRepoId).map { repositoryId ->
            GitHubDataStore.saveIssue(repositoryId, record)
            GitHubDataStore.markSynced(repositoryId)
            GitHubIssueActivity(repositoryId, action, record)
        }
    }

    private fun handleCheckSuiteEvent(payload: JsonObject) {
        val suite = payload["check_suite"]?.jsonObject ?: return
        val repo = payload["repository"]?.jsonObject ?: return
        val githubRepoId = repo["id"]?.jsonPrimitive?.longOrNull ?: return
        val suiteId = suite["id"]?.jsonPrimitive?.longOrNull ?: return
        val headSha = suite["head_sha"]?.jsonPrimitive?.contentOrNull ?: return
        val status = suite["status"]?.jsonPrimitive?.contentOrNull ?: return
        val conclusion = suite["conclusion"]?.jsonPrimitive?.contentOrNull

        linkedRepositoryIds(githubRepoId).forEach { repositoryId ->
            GitHubDataStore.saveCheckSuite(repositoryId, suiteId, headSha, status, conclusion)
        }
    }

    private fun installationId(payload: JsonObject): Long? =
        payload["installation"]?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull

    private fun handleInstallationEvent(payload: JsonObject) {
        val action = payload["action"]?.jsonPrimitive?.contentOrNull
        if (action != "deleted" && action != "suspend") return
        val installationId = installationId(payload) ?: return
        GitHubAuthService.forgetInstallation(installationId)
        GitHubConnectionsTable.deleteWhere { GitHubConnectionsTable.installationId eq installationId }
    }

    private fun handleInstallationRepositoriesEvent(payload: JsonObject) {
        if (payload["action"]?.jsonPrimitive?.contentOrNull != "removed") return
        val installationId = installationId(payload) ?: return
        val removedRepoIds = payload["repositories_removed"]?.jsonArray
            ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.longOrNull }
            .orEmpty()
        if (removedRepoIds.isEmpty()) return

        val connectionIds = GitHubConnectionsTable.selectAll()
            .where { GitHubConnectionsTable.installationId eq installationId }
            .map { it[GitHubConnectionsTable.id] }
        if (connectionIds.isEmpty()) return

        GitHubRepositoriesTable.deleteWhere {
            (GitHubRepositoriesTable.connectionId inList connectionIds) and
                (GitHubRepositoriesTable.githubRepoId inList removedRepoIds)
        }
    }
}
