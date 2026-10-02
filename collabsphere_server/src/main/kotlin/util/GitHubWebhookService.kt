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
import org.jetbrains.exposed.sql.transactions.transaction
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

data class GitHubReleaseActivity(
    override val repositoryId: Int,
    val action: String,
    val release: ReleaseRecord,
    val url: String?,
    val announce: Boolean = true
) : GitHubActivity

data class GitHubCheckSuiteActivity(
    override val repositoryId: Int,
    val headSha: String,
    val status: String,
    val conclusion: String?,
    val headBranch: String?,
    val url: String?,
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
                "release" -> handleReleaseEvent(payload)
                "check_suite" -> handleCheckSuiteEvent(payload)
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

        // Feature E: Extract assignee IDs for sync
        val assigneeIds = try {
            val assigneesArray = (issueJson as? kotlinx.serialization.json.JsonObject)
                ?.get("assignees")
            if (assigneesArray != null) {
                json.decodeFromString<List<com.collabsphere.util.GitHubService.GitHubAssignee>>(assigneesArray.toString())
                    .map { it.id }
            } else emptyList()
        } catch (_: Exception) { emptyList<Long>() }

        return linkedRepositoryIds(githubRepoId).map { repositoryId ->
            GitHubDataStore.saveIssue(repositoryId, record)
            GitHubDataStore.markSynced(repositoryId)

            // Feature E: Trigger assignee sync from GitHub → CollabSphere
            if (action in listOf("assigned", "unassigned", "opened", "edited")) {
                val workspaceId = GitHubRepositoriesTable.selectAll()
                    .where { GitHubRepositoriesTable.id eq repositoryId }
                    .singleOrNull()?.get(GitHubRepositoriesTable.workspaceId)
                if (workspaceId != null) {
                    try {
                        GitHubAssigneeSyncService.refreshIdentityMappings(workspaceId)
                        GitHubAssigneeSyncService.syncFromGitHub(repositoryId, record.number, assigneeIds, workspaceId)
                    } catch (e: Exception) {
                        println("[GitHub] Assignee sync from GitHub failed: ${e.message}")
                    }
                }
            }

            GitHubIssueActivity(repositoryId, action, record)
        }
    }

    private fun handleReleaseEvent(payload: JsonObject): List<GitHubActivity> {
        val action = payload["action"]?.jsonPrimitive?.content ?: return emptyList()
        val repo = payload["repository"]?.jsonObject ?: return emptyList()
        val githubRepoId = repo["id"]?.jsonPrimitive?.longOrNull ?: return emptyList()
        val release = payload["release"]?.jsonObject ?: return emptyList()

        val releaseInfo = json.decodeFromJsonElement<GitHubReleaseInfo>(release)
        val releaseRecord = releaseInfo.toRecord()

        val activities = linkedRepositoryIds(githubRepoId).mapNotNull { repositoryId ->
            transaction { GitHubDataStore.saveRelease(repositoryId, releaseRecord) }

            if (action in listOf("published", "released")) {
                GitHubReleaseActivity(
                    repositoryId = repositoryId,
                    action = action,
                    release = releaseRecord,
                    url = releaseInfo.html_url
                )
            } else {
                null
            }
        }
        return activities
    }

    private fun handleCheckSuiteEvent(payload: JsonObject): List<GitHubCheckSuiteActivity> {
        val suite = payload["check_suite"]?.jsonObject ?: return emptyList()
        val repo = payload["repository"]?.jsonObject ?: return emptyList()
        val githubRepoId = repo["id"]?.jsonPrimitive?.longOrNull ?: return emptyList()
        val suiteId = suite["id"]?.jsonPrimitive?.longOrNull ?: return emptyList()
        val headSha = suite["head_sha"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        val status = suite["status"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        val conclusion = suite["conclusion"]?.jsonPrimitive?.contentOrNull

        val headBranch = suite["head_branch"]?.jsonPrimitive?.contentOrNull
        val appName = suite["app"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
        val url = suite["url"]?.jsonPrimitive?.contentOrNull
        val createdAt = parseGitHubTime(suite["created_at"]?.jsonPrimitive?.contentOrNull)
        val updatedAt = parseGitHubTime(suite["updated_at"]?.jsonPrimitive?.contentOrNull) ?: System.currentTimeMillis()

        return linkedRepositoryIds(githubRepoId).map { repositoryId ->
            GitHubDataStore.saveCheckSuite(
                repositoryId = repositoryId, 
                suiteId = suiteId, 
                headSha = headSha, 
                status = status, 
                conclusion = conclusion,
                headBranch = headBranch,
                appName = appName,
                url = url,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
            GitHubCheckSuiteActivity(
                repositoryId = repositoryId,
                headSha = headSha,
                status = status,
                conclusion = conclusion,
                headBranch = headBranch,
                url = url
            )
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
