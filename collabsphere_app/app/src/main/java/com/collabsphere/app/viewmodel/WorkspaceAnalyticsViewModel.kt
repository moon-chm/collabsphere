package com.collabsphere.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.dto.workspace.WorkspaceAnalyticsResponse
import com.collabsphere.app.remote.workspace.WorkspaceApiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The time windows the Insights screen offers; [key] is what the server expects. */
enum class AnalyticsRange(val key: String, val label: String) {
    WEEK("7d", "7 days"),
    MONTH("30d", "30 days"),
    QUARTER("90d", "90 days"),
    ALL("all", "All time")
}

sealed interface AnalyticsUiState {
    data object Loading : AnalyticsUiState
    data class Loaded(val data: WorkspaceAnalyticsResponse, val isRefreshing: Boolean = false) : AnalyticsUiState
    data class Error(val message: String) : AnalyticsUiState
}

class WorkspaceAnalyticsViewModel(
    private val api: WorkspaceApiService,
    private val workspaceId: Int,
    private val diskCache: com.collabsphere.app.model.SwrCache? = null
) : ViewModel() {

    private val _range = MutableStateFlow(AnalyticsRange.MONTH)
    val range: StateFlow<AnalyticsRange> = _range.asStateFlow()

    private val _state = MutableStateFlow<AnalyticsUiState>(AnalyticsUiState.Loading)
    val state: StateFlow<AnalyticsUiState> = _state.asStateFlow()

    // Ranges already fetched this visit, so switching back and forth is instant; each switch still
    // refreshes in the background.
    private val cache = mutableMapOf<AnalyticsRange, WorkspaceAnalyticsResponse>()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onRangeChange(range: AnalyticsRange) {
        if (range == _range.value) return
        _range.value = range
        load()
    }

    fun refresh() = load()

    private fun load() {
        val range = _range.value
        loadJob?.cancel()
        val cached = cache[range]
        _state.value = if (cached != null) AnalyticsUiState.Loaded(cached, isRefreshing = true) else AnalyticsUiState.Loading
        loadJob = viewModelScope.launch {
            val diskKey = "workspace_insights_${workspaceId}_${range.key}"
            if (cached == null) {
                diskCache?.read(diskKey, WorkspaceAnalyticsResponse.serializer())?.let { saved ->
                    cache[range] = saved.value
                    _state.value = AnalyticsUiState.Loaded(saved.value, isRefreshing = true)
                }
            }
            try {
                val data = api.getAnalytics(workspaceId, range.key)
                cache[range] = data
                _state.value = AnalyticsUiState.Loaded(data)
                diskCache?.write(diskKey, WorkspaceAnalyticsResponse.serializer(), data)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep showing what we had; only an empty screen turns into an error.
                val fallback = cache[range]
                _state.value = if (fallback != null) AnalyticsUiState.Loaded(fallback)
                else AnalyticsUiState.Error("Couldn't load insights. Check your connection and try again.")
            }
        }
    }
}
