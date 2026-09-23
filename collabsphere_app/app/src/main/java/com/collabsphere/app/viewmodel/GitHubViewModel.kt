package com.collabsphere.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.AppConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class GitHubAnalyticsResponse(
    val isConnected: Boolean,
    val hasConnection: Boolean = false,
    val repositoryName: String?,
    val totalCommits: Int,
    val openPullRequests: Int,
    val mergedPullRequests: Int
)

@Serializable
data class AvailableRepo(
    val id: Long,
    val fullName: String,
    val owner: String,
    val name: String,
    val isPrivate: Boolean,
    val htmlUrl: String,
    val defaultBranch: String
)

@Serializable
data class LinkRepoRequest(
    val repoId: Long,
    val fullName: String,
    val owner: String,
    val name: String,
    val isPrivate: Boolean,
    val htmlUrl: String,
    val defaultBranch: String
)

class GitHubViewModel(
    private val client: HttpClient
) : ViewModel() {

    private val _analytics = MutableStateFlow<GitHubAnalyticsResponse?>(null)
    val analytics: StateFlow<GitHubAnalyticsResponse?> = _analytics

    private val _availableRepos = MutableStateFlow<List<AvailableRepo>>(emptyList())
    val availableRepos: StateFlow<List<AvailableRepo>> = _availableRepos

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLinking = MutableStateFlow(false)
    val isLinking: StateFlow<Boolean> = _isLinking

    fun loadAnalytics(workspaceId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = com.collabsphere.app.AuthTokenHolder.token
                val response = client.get("${AppConfig.BASE_URL}/api/workspace/$workspaceId/github/analytics") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                if (response.status == HttpStatusCode.OK) {
                    val result = response.body<GitHubAnalyticsResponse>()
                    _analytics.value = result
                    
                    // If user has a connection but no repo linked yet, auto-fetch available repos
                    if (!result.isConnected && result.hasConnection) {
                        loadAvailableRepos(workspaceId)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadAvailableRepos(workspaceId: Int) {
        viewModelScope.launch {
            try {
                val token = com.collabsphere.app.AuthTokenHolder.token
                val response = client.get("${AppConfig.BASE_URL}/api/workspace/$workspaceId/github/available-repos") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                if (response.status == HttpStatusCode.OK) {
                    _availableRepos.value = response.body<List<AvailableRepo>>()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun linkRepo(workspaceId: Int, repo: AvailableRepo) {
        viewModelScope.launch {
            _isLinking.value = true
            try {
                val token = com.collabsphere.app.AuthTokenHolder.token
                val response = client.post("${AppConfig.BASE_URL}/api/workspace/$workspaceId/github/link-repo") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(LinkRepoRequest(
                        repoId = repo.id,
                        fullName = repo.fullName,
                        owner = repo.owner,
                        name = repo.name,
                        isPrivate = repo.isPrivate,
                        htmlUrl = repo.htmlUrl,
                        defaultBranch = repo.defaultBranch
                    ))
                }
                if (response.status == HttpStatusCode.OK) {
                    // Refresh analytics — now it should show as connected
                    loadAnalytics(workspaceId)
                    _availableRepos.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLinking.value = false
            }
        }
    }
}
