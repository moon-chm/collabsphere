package com.collabsphere.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.AppConfig
import com.collabsphere.app.AuthTokenHolder
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GitHubUnfurlViewModel(private val httpClient: HttpClient) : ViewModel() {

    private val _previews = MutableStateFlow<Map<String, GitHubPreviewItem>>(emptyMap())
    val previews: StateFlow<Map<String, GitHubPreviewItem>> = _previews.asStateFlow()

    private val queuedUrls = mutableSetOf<String>()
    private val fetchedUrls = mutableSetOf<String>()
    private var batchJob: Job? = null

    // Helper regex to quickly check if a URL looks like GitHub to avoid queuing unnecessary stuff
    private val GITHUB_URL_REGEX = Regex("^https://github\\.com/([^/]+)/([^/]+)/.*$")

    fun requestUnfurl(workspaceId: Int, url: String) {
        if (!GITHUB_URL_REGEX.matches(url)) return
        if (fetchedUrls.contains(url)) return

        synchronized(this) {
            if (queuedUrls.add(url)) {
                scheduleBatch(workspaceId)
            }
        }
    }

    private fun scheduleBatch(workspaceId: Int) {
        if (batchJob?.isActive == true) return
        batchJob = viewModelScope.launch {
            delay(250) // Small debounce window to batch URLs from a screen load
            val batch = synchronized(this@GitHubUnfurlViewModel) {
                val urls = queuedUrls.toList()
                queuedUrls.clear()
                fetchedUrls.addAll(urls)
                urls
            }
            
            if (batch.isEmpty()) return@launch

            val token = AuthTokenHolder.token ?: return@launch
            try {
                val response = httpClient.post("${AppConfig.BASE_URL}/api/workspace/$workspaceId/github/unfurl") {
                    // Token is automatically injected by DynamicTokenPlugin
                    // header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(GitHubUnfurlRequest(batch))
                }
                
                if (response.status.isSuccess()) {
                    val unfurlResponse = response.body<GitHubUnfurlResponse>()
                    _previews.value = _previews.value + unfurlResponse.previews
                }
            } catch (e: Exception) {
                // If it fails, we remove them from fetchedUrls so they can be retried later if requested again
                synchronized(this@GitHubUnfurlViewModel) {
                    fetchedUrls.removeAll(batch.toSet())
                }
            }
        }
    }

    private val _actionStates = MutableStateFlow<Map<String, GitHubActionClientState>>(emptyMap())
    val actionStates: StateFlow<Map<String, GitHubActionClientState>> = _actionStates.asStateFlow()

    private val _actionMessages = MutableStateFlow<Map<String, String>>(emptyMap())
    val actionMessages: StateFlow<Map<String, String>> = _actionMessages.asStateFlow()

    fun performAction(workspaceId: Int, url: String, action: String, body: String? = null, idempotencyKey: String = java.util.UUID.randomUUID().toString()) {
        if (_actionStates.value[url] == GitHubActionClientState.Executing) return
        
        _actionStates.value = _actionStates.value + (url to GitHubActionClientState.Executing)
        _actionMessages.value = _actionMessages.value - url
        
        viewModelScope.launch {
            val token = AuthTokenHolder.token ?: run {
                _actionStates.value = _actionStates.value + (url to GitHubActionClientState.AuthenticationRequired)
                return@launch
            }
            try {
                val response = httpClient.post("${AppConfig.BASE_URL}/api/workspace/$workspaceId/github/action") {
                    // Token is automatically injected by DynamicTokenPlugin
                    // header(HttpHeaders.Authorization, "Bearer $token")
                    header("Idempotency-Key", idempotencyKey)
                    contentType(ContentType.Application.Json)
                    setBody(GitHubActionRequest(url, action, body))
                }
                
                if (response.status.isSuccess()) {
                    val actionResp = response.body<GitHubActionResponse>()
                    val mappedState = when(actionResp.status) {
                        GitHubActionResultStatus.ActionSucceeded -> GitHubActionClientState.Succeeded
                        GitHubActionResultStatus.ActionAlreadyApplied -> GitHubActionClientState.AlreadyApplied
                        GitHubActionResultStatus.AuthenticationRequired -> GitHubActionClientState.AuthenticationRequired
                        GitHubActionResultStatus.PermissionDenied -> GitHubActionClientState.PermissionDenied
                        GitHubActionResultStatus.ResourceNotFound -> GitHubActionClientState.Failed
                        GitHubActionResultStatus.NotMergeable -> GitHubActionClientState.NotMergeable
                        GitHubActionResultStatus.HeadChanged -> GitHubActionClientState.Conflict
                        GitHubActionResultStatus.Conflict -> GitHubActionClientState.Conflict
                        GitHubActionResultStatus.RateLimited -> GitHubActionClientState.RateLimited
                        GitHubActionResultStatus.ValidationFailed -> GitHubActionClientState.Failed
                        GitHubActionResultStatus.GitHubUnavailable -> GitHubActionClientState.GitHubUnavailable
                        GitHubActionResultStatus.UnknownFailure -> GitHubActionClientState.Failed
                    }
                    _actionStates.value = _actionStates.value + (url to mappedState)
                    _actionMessages.value = _actionMessages.value + (url to actionResp.message)
                    
                    if (actionResp.status == GitHubActionResultStatus.ActionSucceeded || actionResp.status == GitHubActionResultStatus.ActionAlreadyApplied) {
                        actionResp.newState?.let { newState ->
                            val currentPreview = _previews.value[url]
                            if (currentPreview != null) {
                                val isMerge = action == "MERGE_PR"
                                val updated = currentPreview.copy(
                                    state = if (isMerge) "closed" else newState,
                                    merged = if (isMerge) true else currentPreview.merged
                                )
                                _previews.value = _previews.value + (url to updated)
                            }
                        }
                    }
                } else {
                    _actionStates.value = _actionStates.value + (url to GitHubActionClientState.Failed)
                }
            } catch (e: Exception) {
                _actionStates.value = _actionStates.value + (url to GitHubActionClientState.GitHubUnavailable)
            }
        }
    }
    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}