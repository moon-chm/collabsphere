package com.collabsphere.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.AppConfig
import com.collabsphere.app.AuthTokenHolder
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.TimeZone

@Serializable
data class GitHubAnalyticsResponse(
    val isConnected: Boolean,
    val hasConnection: Boolean = false,
    val canManage: Boolean = false,
    val repositoryName: String?,
    val repositoryUrl: String? = null,
    val lastSyncedAt: Long? = null,
    val isSyncing: Boolean = false,
    val totalCommits: Int,
    val openPullRequests: Int,
    val mergedPullRequests: Int,
    val topContributors: List<GitHubContributorStats> = emptyList(),
    val commitActivity: List<GitHubDailyCount> = emptyList(),
    val recentCommits: List<GitHubCommitItem> = emptyList(),
    val recentPullRequests: List<GitHubPullRequestItem> = emptyList(),
    val notifyChannelId: Int? = null,
    val channels: List<GitHubChannelOption> = emptyList()
)

@Serializable
data class GitHubChannelOption(
    val id: Int,
    val name: String
)

@Serializable
data class GitHubNotifyChannelRequest(
    val channelId: Int? = null
)

@Serializable
data class GitHubTaskLink(
    val kind: String,
    val ref: String,
    val title: String,
    val url: String,
    val createdAt: Long
)

@Serializable
data class GitHubContributorStats(
    val username: String,
    val commits: Int
)

@Serializable
data class GitHubDailyCount(
    val date: String,
    val count: Int
)

@Serializable
data class GitHubCommitItem(
    val sha: String,
    val message: String,
    val authorName: String? = null,
    val commitDate: Long
)

@Serializable
data class GitHubPullRequestItem(
    val number: Int,
    val title: String,
    val state: String,
    val authorUsername: String,
    val createdAt: Long,
    val mergedAt: Long? = null
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

@Serializable
data class GitHubInstallUrlResponse(
    val url: String
)

data class GitHubAuthResult(
    val workspaceId: Int?,
    val error: String?
)

object GitHubAuthEvents {
    private val _result = MutableStateFlow<GitHubAuthResult?>(null)
    val result: StateFlow<GitHubAuthResult?> = _result.asStateFlow()

    fun publish(uri: Uri?): Boolean {
        if (uri?.scheme != "collabsphere" || uri.host != "github-auth-success") return false
        _result.value = GitHubAuthResult(
            workspaceId = uri.getQueryParameter("workspace_id")?.toIntOrNull(),
            error = uri.getQueryParameter("error")
        )
        return true
    }

    fun consume() {
        _result.value = null
    }
}

private const val SYNC_POLL_INTERVAL_MS = 3_000L

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

    private val _isLoadingRepos = MutableStateFlow(false)
    val isLoadingRepos: StateFlow<Boolean> = _isLoadingRepos

    private val _isPickingRepo = MutableStateFlow(false)
    val isPickingRepo: StateFlow<Boolean> = _isPickingRepo

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var pollJob: Job? = null

    private fun api(workspaceId: Int, path: String) =
        "${AppConfig.BASE_URL}/api/workspace/$workspaceId/github/$path"

    private fun HttpRequestBuilder.auth() {
        header(HttpHeaders.Authorization, "Bearer ${AuthTokenHolder.token}")
    }

    private suspend fun HttpResponse.errorMessage(fallback: String): String =
        runCatching { bodyAsText() }.getOrNull()?.takeIf { it.isNotBlank() && it.length < 200 } ?: fallback

    fun clearError() {
        _error.value = null
    }

    fun reportAuthError(code: String) {
        _error.value = when (code) {
            "not_workspace_owner" -> "Only the workspace owner can connect GitHub."
            "installation_not_found" -> "The GitHub App installation could not be found. Please try again."
            "invalid_request" -> "The GitHub sign-in link expired. Please try again."
            "token_exchange_failed" -> "GitHub sign-in failed. Please try again."
            else -> "Could not connect GitHub. Please try again."
        }
    }

    private suspend fun fetchAnalytics(workspaceId: Int) {
        try {
            val response = client.get(api(workspaceId, "analytics")) {
                auth()
                parameter("tz", TimeZone.getDefault().id)
            }
            if (response.status == HttpStatusCode.OK) {
                val result = response.body<GitHubAnalyticsResponse>()
                _analytics.value = result
                if (result.isSyncing) pollWhileSyncing(workspaceId)
                if (!result.isConnected && result.hasConnection && result.canManage) {
                    fetchAvailableRepos(workspaceId)
                }
            } else {
                _error.value = response.errorMessage("Could not load GitHub data")
            }
        } catch (e: Exception) {
            _error.value = "Could not reach the server"
        }
    }

    private suspend fun fetchAvailableRepos(workspaceId: Int) {
        _isLoadingRepos.value = true
        try {
            val response = client.get(api(workspaceId, "available-repos")) { auth() }
            if (response.status == HttpStatusCode.OK) {
                _availableRepos.value = response.body<List<AvailableRepo>>()
            } else {
                _error.value = response.errorMessage("Could not load repositories")
            }
        } catch (e: Exception) {
            _error.value = "Could not reach the server"
        } finally {
            _isLoadingRepos.value = false
        }
    }

    fun loadAnalytics(workspaceId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            fetchAnalytics(workspaceId)
            _isLoading.value = false
        }
    }

    private fun pollWhileSyncing(workspaceId: Int) {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (_analytics.value?.isSyncing == true) {
                delay(SYNC_POLL_INTERVAL_MS)
                fetchAnalytics(workspaceId)
                if (_error.value != null) {
                    _analytics.value = _analytics.value?.copy(isSyncing = false)
                }
            }
        }
    }

    fun syncNow(workspaceId: Int) {
        viewModelScope.launch {
            try {
                val response = client.post(api(workspaceId, "sync")) { auth() }
                if (response.status == HttpStatusCode.Accepted) {
                    _analytics.value = _analytics.value?.copy(isSyncing = true)
                    pollWhileSyncing(workspaceId)
                } else {
                    _error.value = response.errorMessage("Could not start sync")
                }
            } catch (e: Exception) {
                _error.value = "Could not reach the server"
            }
        }
    }

    suspend fun loadTaskLinks(workspaceId: Int, taskId: Int): List<GitHubTaskLink> =
        try {
            val response = client.get(api(workspaceId, "tasks/$taskId/links")) { auth() }
            if (response.status == HttpStatusCode.OK) response.body() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    fun setNotifyChannel(workspaceId: Int, channelId: Int?) {
        viewModelScope.launch {
            try {
                val response = client.post(api(workspaceId, "notify-channel")) {
                    auth()
                    contentType(ContentType.Application.Json)
                    setBody(GitHubNotifyChannelRequest(channelId))
                }
                if (response.status == HttpStatusCode.OK) {
                    _analytics.value = _analytics.value?.copy(notifyChannelId = channelId)
                } else {
                    _error.value = response.errorMessage("Could not update the notification channel")
                }
            } catch (e: Exception) {
                _error.value = "Could not reach the server"
            }
        }
    }

    fun loadAvailableRepos(workspaceId: Int) {
        viewModelScope.launch { fetchAvailableRepos(workspaceId) }
    }

    fun startInstall(workspaceId: Int, onUrlReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = client.get(api(workspaceId, "install-url")) { auth() }
                if (response.status == HttpStatusCode.OK) {
                    onUrlReady(response.body<GitHubInstallUrlResponse>().url)
                } else {
                    _error.value = response.errorMessage("Could not start GitHub connection")
                }
            } catch (e: Exception) {
                _error.value = "Could not reach the server"
            }
        }
    }

    fun startChangeRepo(workspaceId: Int) {
        _isPickingRepo.value = true
        loadAvailableRepos(workspaceId)
    }

    fun cancelChangeRepo() {
        _isPickingRepo.value = false
    }

    fun linkRepo(workspaceId: Int, repo: AvailableRepo) {
        viewModelScope.launch {
            _isLinking.value = true
            try {
                val response = client.post(api(workspaceId, "link-repo")) {
                    auth()
                    contentType(ContentType.Application.Json)
                    setBody(
                        LinkRepoRequest(
                            repoId = repo.id,
                            fullName = repo.fullName,
                            owner = repo.owner,
                            name = repo.name,
                            isPrivate = repo.isPrivate,
                            htmlUrl = repo.htmlUrl,
                            defaultBranch = repo.defaultBranch
                        )
                    )
                }
                if (response.status == HttpStatusCode.OK) {
                    _availableRepos.value = emptyList()
                    _isPickingRepo.value = false
                    fetchAnalytics(workspaceId)
                } else {
                    _error.value = response.errorMessage("Could not link ${repo.fullName}")
                }
            } catch (e: Exception) {
                _error.value = "Could not reach the server"
            } finally {
                _isLinking.value = false
            }
        }
    }

    fun disconnectGitHub(workspaceId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = client.post(api(workspaceId, "disconnect")) { auth() }
                if (response.status == HttpStatusCode.OK) {
                    _availableRepos.value = emptyList()
                    _isPickingRepo.value = false
                    fetchAnalytics(workspaceId)
                } else {
                    _error.value = response.errorMessage("Could not disconnect GitHub")
                }
            } catch (e: Exception) {
                _error.value = "Could not reach the server"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
