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

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun HttpRequestBuilder.githubHeaders(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.Accept, "application/vnd.github+json")
    }

    private suspend inline fun <reified T> getPaged(token: String, url: String, maxPages: Int): List<T>? {
        val results = mutableListOf<T>()
        for (page in 1..maxPages) {
            val response = httpClient.get(url) {
                githubHeaders(token)
                parameter("per_page", PAGE_SIZE)
                parameter("page", page)
            }
            if (!response.status.isSuccess()) {
                println("[GitHub] GET $url page $page failed: ${response.status}")
                return if (page == 1) null else results
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

    private suspend fun fetchRepositoryPages(token: String, url: String): List<GitHubRepository>? {
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
                    return if (page == 1) null else results
                }
                val repos = response.body<GitHubRepositoriesResponse>().repositories
                results += repos
                if (repos.size < PAGE_SIZE) break
            }
            return results
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun getCommits(token: String, repoFullName: String, branch: String, since: Long?): List<GitHubCommitInfo>? =
        try {
            val sinceParam = since?.let { "&since=${java.time.Instant.ofEpochMilli(it)}" } ?: ""
            getPaged<GitHubCommitInfo>(
                token,
                "https://api.github.com/repos/$repoFullName/commits?sha=${branch.encodeURLQueryComponent()}$sinceParam",
                MAX_COMMIT_PAGES
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    suspend fun getPullRequests(token: String, repoFullName: String, incremental: Boolean): List<GitHubPullRequestInfo>? =
        try {
            getPaged<GitHubPullRequestInfo>(
                token,
                "https://api.github.com/repos/$repoFullName/pulls?state=all&sort=updated&direction=desc",
                if (incremental) INCREMENTAL_PR_PAGES else MAX_PR_PAGES
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
}

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
    val merged_at: String? = null
)
