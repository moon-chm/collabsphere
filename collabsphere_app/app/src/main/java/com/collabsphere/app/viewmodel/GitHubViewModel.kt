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
    val repositoryName: String?,
    val totalCommits: Int,
    val openPullRequests: Int,
    val mergedPullRequests: Int
)

class GitHubViewModel(
    private val client: HttpClient
) : ViewModel() {

    private val _analytics = MutableStateFlow<GitHubAnalyticsResponse?>(null)
    val analytics: StateFlow<GitHubAnalyticsResponse?> = _analytics

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadAnalytics(workspaceId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = com.collabsphere.app.AuthTokenHolder.token
                val response = client.get("${AppConfig.BASE_URL}/api/workspace/$workspaceId/github/analytics") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                if (response.status == HttpStatusCode.OK) {
                    _analytics.value = response.body<GitHubAnalyticsResponse>()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
