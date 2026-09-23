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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement

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

object GitHubService {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /**
     * Gets the user's installations for this app using their User Access Token.
     */
    suspend fun getUserInstallations(userAccessToken: String): List<GitHubInstallation>? {
        try {
            val response = httpClient.get("https://api.github.com/user/installations") {
                header(HttpHeaders.Authorization, "Bearer $userAccessToken")
                header(HttpHeaders.Accept, "application/vnd.github.v3+json")
            }
            if (response.status.isSuccess()) {
                val data = response.body<GitHubInstallationsResponse>()
                return data.installations
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Gets the repositories that the user has granted the installation access to.
     */
    suspend fun getInstallationRepositories(userAccessToken: String, installationId: Long): List<GitHubRepository>? {
        try {
            val response = httpClient.get("https://api.github.com/user/installations/$installationId/repositories") {
                header(HttpHeaders.Authorization, "Bearer $userAccessToken")
                header(HttpHeaders.Accept, "application/vnd.github.v3+json")
            }
            if (response.status.isSuccess()) {
                val data = response.body<GitHubRepositoriesResponse>()
                return data.repositories
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
