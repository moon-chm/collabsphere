package com.collabsphere.util

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

class GitHubApiException(val statusCode: Int, message: String) : Exception(message)

@Serializable
data class GitHubRepository(
    val id: Long,
    val owner: GitHubOwner,
    val name: String,
    val full_name: String,
    val private: Boolean,
    val html_url: String,
    val default_branch: String
)

@Serializable
data class GitHubOwner(
    val login: String
)

@Serializable
data class GitHubUser(
    val id: Long,
    val login: String
)

@Serializable
data class GitHubInstallationsResponse(
    val total_count: Int,
    val installations: List<GitHubInstallation>
)

@Serializable
data class GitHubInstallation(
    val id: Long,
    val account: GitHubOwner
)

@Serializable
data class GitHubRepositoriesResponse(
    val total_count: Int,
    val repositories: List<GitHubRepository>
)

fun parseGitHubTime(value: String?): Long? =
    value?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

object GitHubService {

    private const val PAGE_SIZE = 100
    private const val MAX_REPO_PAGES = 10
    private const val MAX_COMMIT_PAGES = 50
    private const val MAX_PR_PAGES = 20
    private const val INCREMENTAL_PR_PAGES = 2
    private const val MAX_ISSUE_PAGES = 10

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun HttpRequestBuilder.githubHeaders(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.Accept, "application/vnd.github+json")
    }

    private suspend inline fun <reified T> getPaged(token: String, url: String, maxPages: Int): List<T> {
        val results = mutableListOf<T>()
        for (page in 1..maxPages) {
            val response = httpClient.get(url) {
                githubHeaders(token)
                parameter("per_page", PAGE_SIZE)
                parameter("page", page)
            }
            if (!response.status.isSuccess()) {
                println("[GitHub] GET $url page $page failed: ${response.status}")
                val rateLimitRemaining = response.headers["X-RateLimit-Remaining"]?.toIntOrNull()
                val msg = if (response.status == HttpStatusCode.Forbidden && rateLimitRemaining == 0) {
                    "GitHub API Rate limit exceeded"
                } else if (response.status == HttpStatusCode.TooManyRequests) {
                    "GitHub API Secondary Rate limit exceeded"
                } else {
                    "GitHub API error: ${response.status}"
                }
                throw GitHubApiException(response.status.value, msg)
            }
            val items = response.body<List<T>>()
            results += items
            if (items.size < PAGE_SIZE) break
        }
        return results
    }

    suspend fun getUserInstallations(userAccessToken: String): List<GitHubInstallation>? {
        try {
            val response = httpClient.get("https://api.github.com/user/installations") {
                githubHeaders(userAccessToken)
            }
            if (response.status.isSuccess()) {
                return response.body<GitHubInstallationsResponse>().installations
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun getAuthenticatedUser(userAccessToken: String): GitHubUser? =
        try {
            val response = httpClient.get("https://api.github.com/user") {
                githubHeaders(userAccessToken)
            }
            if (response.status.isSuccess()) response.body<GitHubUser>() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    suspend fun repoAccessToken(installationId: Long, userAccessToken: String?): String? =
        GitHubAuthService.getInstallationToken(installationId) ?: userAccessToken?.takeIf { it.isNotBlank() }

    suspend fun listInstallationRepositories(installationId: Long, userAccessToken: String?): List<GitHubRepository>? {
        val installationToken = GitHubAuthService.getInstallationToken(installationId)
        return when {
            installationToken != null ->
                fetchRepositoryPages(installationToken, "https://api.github.com/installation/repositories")
            !userAccessToken.isNullOrBlank() ->
                fetchRepositoryPages(userAccessToken, "https://api.github.com/user/installations/$installationId/repositories")
            else -> null
        }
    }

    private suspend fun fetchRepositoryPages(token: String, url: String): List<GitHubRepository> {
        try {
            val results = mutableListOf<GitHubRepository>()
            for (page in 1..MAX_REPO_PAGES) {
                val response = httpClient.get(url) {
                    githubHeaders(token)
                    parameter("per_page", PAGE_SIZE)
                    parameter("page", page)
                }
                if (!response.status.isSuccess()) {
                    println("[GitHub] Listing installation repos failed: ${response.status}")
                    val rateLimitRemaining = response.headers["X-RateLimit-Remaining"]?.toIntOrNull()
                    val msg = if (response.status == HttpStatusCode.Forbidden && rateLimitRemaining == 0) {
                        "GitHub API Rate limit exceeded"
                    } else if (response.status == HttpStatusCode.TooManyRequests) {
                        "GitHub API Secondary Rate limit exceeded"
                    } else {
                        "GitHub API error: ${response.status}"
                    }
                    throw GitHubApiException(response.status.value, msg)
                }
                val repos = response.body<GitHubRepositoriesResponse>().repositories
                results += repos
                if (repos.size < PAGE_SIZE) break
            }
            return results
        } catch (e: GitHubApiException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to fetch repository pages", e)
        }
    }

    suspend fun getCommits(token: String, repoFullName: String, branch: String, since: Long?): List<GitHubCommitInfo> =
        try {
            val sinceParam = since?.let { "&since=${java.time.Instant.ofEpochMilli(it)}" } ?: ""
            getPaged<GitHubCommitInfo>(
                token,
                "https://api.github.com/repos/$repoFullName/commits?sha=${branch.encodeURLQueryComponent()}$sinceParam",
                MAX_COMMIT_PAGES
            )
        } catch (e: GitHubApiException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

    suspend fun getIssues(token: String, repoFullName: String, incremental: Boolean): List<GitHubIssueInfo> =
        try {
            getPaged<GitHubIssueInfo>(
                token,
                "https://api.github.com/repos/$repoFullName/issues?state=all&sort=updated&direction=desc",
                if (incremental) INCREMENTAL_PR_PAGES else MAX_ISSUE_PAGES
            ).filter { it.pull_request == null }
        } catch (e: GitHubApiException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

    suspend fun createIssue(token: String, repoFullName: String, title: String, body: String): Pair<GitHubIssueInfo?, HttpStatusCode?> =
        try {
            val response = httpClient.post("https://api.github.com/repos/$repoFullName/issues") {
                githubHeaders(token)
                contentType(ContentType.Application.Json)
                setBody(mapOf("title" to title, "body" to body))
            }
            if (response.status.isSuccess()) {
                response.body<GitHubIssueInfo>() to response.status
            } else {
                println("[GitHub] Creating issue in $repoFullName failed: ${response.status}")
                null to response.status
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null to null
        }

    suspend fun setIssueState(token: String, repoFullName: String, number: Int, state: String): Boolean =
        try {
            val response = httpClient.patch("https://api.github.com/repos/$repoFullName/issues/$number") {
                githubHeaders(token)
                contentType(ContentType.Application.Json)
                setBody(mapOf("state" to state))
            }
            if (!response.status.isSuccess()) {
                println("[GitHub] Setting issue #$number in $repoFullName to $state failed: ${response.status}")
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

    suspend fun updateIssueState(token: String, repoFullName: String, number: Int, state: String, stateReason: String? = null): Pair<GitHubIssueInfo?, HttpStatusCode> =
        try {
            val bodyMap = mutableMapOf("state" to state)
            if (stateReason != null) {
                bodyMap["state_reason"] = stateReason
            }
            val response = httpClient.patch("https://api.github.com/repos/$repoFullName/issues/$number") {
                githubHeaders(token)
                contentType(ContentType.Application.Json)
                setBody(bodyMap)
            }
            if (response.status.isSuccess()) {
                Pair(response.body<GitHubIssueInfo>(), response.status)
            } else {
                Pair(null, response.status)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(null, HttpStatusCode.InternalServerError)
        }

    suspend fun addIssueComment(token: String, repoFullName: String, number: Int, body: String): Pair<GitHubIssueComment?, HttpStatusCode> =
        try {
            val response = httpClient.post("https://api.github.com/repos/$repoFullName/issues/$number/comments") {
                githubHeaders(token)
                contentType(ContentType.Application.Json)
                setBody(mapOf("body" to body))
            }
            if (response.status.isSuccess()) {
                Pair(response.body<GitHubIssueComment>(), response.status)
            } else {
                Pair(null, response.status)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(null, HttpStatusCode.InternalServerError)
        }

    suspend fun updatePullRequestState(token: String, repoFullName: String, number: Int, state: String): Pair<GitHubPullRequestInfo?, HttpStatusCode> =
        try {
            val response = httpClient.patch("https://api.github.com/repos/$repoFullName/pulls/$number") {
                githubHeaders(token)
                contentType(ContentType.Application.Json)
                setBody(mapOf("state" to state))
            }
            if (response.status.isSuccess()) {
                Pair(response.body<GitHubPullRequestInfo>(), response.status)
            } else {
                Pair(null, response.status)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(null, HttpStatusCode.InternalServerError)
        }

    suspend fun approvePullRequest(token: String, repoFullName: String, number: Int): HttpStatusCode =
        try {
            val response = httpClient.post("https://api.github.com/repos/$repoFullName/pulls/$number/reviews") {
                githubHeaders(token)
                contentType(ContentType.Application.Json)
                setBody(mapOf("event" to "APPROVE"))
            }
            response.status
        } catch (e: Exception) {
            e.printStackTrace()
            HttpStatusCode.InternalServerError
        }

    suspend fun mergePullRequest(token: String, repoFullName: String, number: Int, mergeMethod: String = "merge"): Pair<GitHubMergeResult?, HttpStatusCode> =
        try {
            val response = httpClient.put("https://api.github.com/repos/$repoFullName/pulls/$number/merge") {
                githubHeaders(token)
                contentType(ContentType.Application.Json)
                setBody(mapOf("merge_method" to mergeMethod))
            }
            if (response.status.isSuccess()) {
                Pair(response.body<GitHubMergeResult>(), response.status)
            } else if (response.status == HttpStatusCode.MethodNotAllowed || response.status == HttpStatusCode.Conflict) {
                try {
                    Pair(response.body<GitHubMergeResult>(), response.status)
                } catch(e: Exception) {
                    Pair(null, response.status)
                }
            } else {
                Pair(null, response.status)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(null, HttpStatusCode.InternalServerError)
        }

    suspend fun getPullRequests(token: String, repoFullName: String, incremental: Boolean): List<GitHubPullRequestInfo> =
        try {
            getPaged<GitHubPullRequestInfo>(
                token,
                "https://api.github.com/repos/$repoFullName/pulls?state=all&sort=updated&direction=desc",
                if (incremental) INCREMENTAL_PR_PAGES else MAX_PR_PAGES
            )
        } catch (e: GitHubApiException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

    suspend fun getPullRequest(token: String, repoFullName: String, number: Int): GitHubPullRequestInfo? =
        try {
            val response = httpClient.get("https://api.github.com/repos/$repoFullName/pulls/$number") {
                githubHeaders(token)
            }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            null
        }

    suspend fun getIssue(token: String, repoFullName: String, number: Int): GitHubIssueInfo? =
        try {
            val response = httpClient.get("https://api.github.com/repos/$repoFullName/issues/$number") {
                githubHeaders(token)
            }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            null
        }

    suspend fun getCommit(token: String, repoFullName: String, sha: String): GitHubCommitInfo? =
        try {
            val response = httpClient.get("https://api.github.com/repos/$repoFullName/commits/$sha") {
                githubHeaders(token)
            }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            null
        }

    suspend fun getReleases(
        token: String,
        repoFullName: String,
        page: Int = 1,
        perPage: Int = 10
    ): List<GitHubReleaseInfo> =
        try {
            val response = httpClient.get("https://api.github.com/repos/$repoFullName/releases") {
                githubHeaders(token)
                url {
                    parameters.append("per_page", perPage.toString())
                    parameters.append("page", page.toString())
                }
            }
            if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    // ── Feature E: Assignee Management ──────────────────────────────────────

    @Serializable
    data class GitHubAssignee(
        val id: Long,
        val login: String
    )

    /** Get current assignees on an issue. Works for PRs too (GitHub treats them as issues). */
    suspend fun getIssueAssignees(token: String, repoFullName: String, number: Int): List<GitHubAssignee>? =
        try {
            val response = httpClient.get("https://api.github.com/repos/$repoFullName/issues/$number") {
                githubHeaders(token)
            }
            if (response.status.isSuccess()) {
                val body = response.body<kotlinx.serialization.json.JsonObject>()
                val assignees = body["assignees"]?.let {
                    kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        .decodeFromString<List<GitHubAssignee>>(it.toString())
                } ?: emptyList()
                assignees
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    /** Add assignees to an issue. GitHub supports up to 10 per call. */
    suspend fun addIssueAssignees(token: String, repoFullName: String, number: Int, logins: List<String>): Boolean =
        try {
            val response = httpClient.post("https://api.github.com/repos/$repoFullName/issues/$number/assignees") {
                githubHeaders(token)
                contentType(ContentType.Application.Json)
                setBody(mapOf("assignees" to logins))
            }
            if (!response.status.isSuccess()) {
                println("[GitHub] Adding assignees to issue #$number in $repoFullName failed: ${response.status}")
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

    /** Remove assignees from an issue. */
    suspend fun removeIssueAssignees(token: String, repoFullName: String, number: Int, logins: List<String>): Boolean =
        try {
            val response = httpClient.delete("https://api.github.com/repos/$repoFullName/issues/$number/assignees") {
                githubHeaders(token)
                contentType(ContentType.Application.Json)
                setBody(mapOf("assignees" to logins))
            }
            if (!response.status.isSuccess()) {
                println("[GitHub] Removing assignees from issue #$number in $repoFullName failed: ${response.status}")
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

    // ── Feature G: File Content Retrieval ────────────────────────────────────

    @Serializable
    data class GitHubFileContent(
        val type: String = "",
        val encoding: String? = null,
        val size: Int = 0,
        val name: String = "",
        val path: String = "",
        val content: String? = null,
        val sha: String? = null,
        val html_url: String? = null
    )

    /**
     * Fetch file content from a GitHub repository at a specific ref.
     * Returns decoded content string, or null if file not found / binary / too large.
     * Max file size enforced at 512KB to prevent memory issues.
     */
    suspend fun getFileContent(
        token: String,
        repoFullName: String,
        filePath: String,
        ref: String
    ): Triple<String?, Boolean, Int>? =
        try {
            val response = httpClient.get("https://api.github.com/repos/$repoFullName/contents/${filePath.trimStart('/')}") {
                githubHeaders(token)
                parameter("ref", ref)
            }
            if (!response.status.isSuccess()) {
                if (response.status == HttpStatusCode.NotFound) {
                    Triple(null, false, 0) // File not found
                } else null
            } else {
                val file = response.body<GitHubFileContent>()
                if (file.type != "file") {
                    Triple(null, false, 0) // Not a file (directory, submodule, etc.)
                } else if (file.size > 512 * 1024) {
                    Triple(null, false, file.size) // Too large
                } else if (file.encoding == "base64" && file.content != null) {
                    val decoded = try {
                        String(java.util.Base64.getMimeDecoder().decode(file.content))
                    } catch (e: Exception) {
                        null // Binary file — can't decode as UTF-8
                    }
                    val isBinary = decoded == null || decoded.contains('\u0000')
                    Triple(if (isBinary) null else decoded, isBinary, file.size)
                } else {
                    Triple(null, true, file.size) // No content or unknown encoding
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    /** Resolve the latest commit SHA for a branch or ref. */
    suspend fun resolveRef(token: String, repoFullName: String, ref: String): String? =
        try {
            val response = httpClient.get("https://api.github.com/repos/$repoFullName/commits/$ref") {
                githubHeaders(token)
                parameter("per_page", 1)
            }
            if (response.status.isSuccess()) {
                response.body<GitHubCommitInfo>().sha
            } else null
        } catch (e: Exception) {
            null
        }
}

@Serializable
data class GitHubIssueInfo(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    val html_url: String,
    val user: GitHubOwner? = null,
    val body: String? = null,
    val created_at: String? = null,
    val closed_at: String? = null,
    val pull_request: kotlinx.serialization.json.JsonElement? = null
)

@Serializable
data class GitHubCommitInfo(
    val sha: String,
    val commit: GitHubCommitDetail? = null
)

@Serializable
data class GitHubCommitDetail(
    val message: String? = null,
    val author: GitHubCommitAuthor? = null
)

@Serializable
data class GitHubCommitAuthor(
    val name: String? = null,
    val email: String? = null,
    val date: String? = null
)

@Serializable
data class GitHubPullRequestInfo(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    val user: GitHubOwner? = null,
    val created_at: String? = null,
    val closed_at: String? = null,
    val merged_at: String? = null,
    val body: String? = null,
    val html_url: String? = null,
    val head: GitHubRef? = null
)

@Serializable
data class GitHubRef(
    val sha: String? = null
)

@Serializable
data class GitHubMergeResult(
    val sha: String? = null,
    val merged: Boolean = false,
    val message: String? = null
)

@Serializable
data class GitHubIssueComment(
    val id: Long,
    val body: String,
    val user: GitHubOwner? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val html_url: String? = null
)

@Serializable
data class GitHubReleaseInfo(
    val id: Long,
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val author: GitHubOwner? = null,
    val html_url: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val published_at: String? = null,
    val created_at: String? = null
)

data class ReleaseRecord(
    val githubReleaseId: Long,
    val tagName: String,
    val name: String?,
    val body: String?,
    val author: String?,
    val htmlUrl: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val publishedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

fun GitHubReleaseInfo.toRecord(now: Long = System.currentTimeMillis()): ReleaseRecord =
    ReleaseRecord(
        githubReleaseId = id,
        tagName = tag_name,
        name = name,
        body = body,
        author = author?.login,
        htmlUrl = html_url,
        draft = draft,
        prerelease = prerelease,
        publishedAt = parseGitHubTime(published_at),
        createdAt = parseGitHubTime(created_at) ?: now,
        updatedAt = now
    )
