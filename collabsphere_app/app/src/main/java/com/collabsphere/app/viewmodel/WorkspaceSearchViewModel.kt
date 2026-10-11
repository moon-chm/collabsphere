package com.collabsphere.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.dto.search.WorkspaceSearchResponse
import com.collabsphere.app.model.AppDatabase
import com.collabsphere.app.remote.workspace.WorkspaceApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SearchUiState {
    data object Idle : SearchUiState()
    data object Loading : SearchUiState()
    data class Results(val response: WorkspaceSearchResponse) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

@OptIn(FlowPreview::class)
class WorkspaceSearchViewModel(
    private val api: WorkspaceApiService,
    private val workspaceId: Int,
    private val appDb: AppDatabase? = null,
    private val currentUserId: Int = -1
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _query
                .map { it.trim() }
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { runSearch(it) }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value.take(MAX_QUERY_LENGTH)
    }

    fun retry() {
        viewModelScope.launch { runSearch(_query.value.trim()) }
    }

    private suspend fun runSearch(query: String) {
        if (query.length < MIN_QUERY_LENGTH) {
            _state.value = SearchUiState.Idle
            return
        }
        _state.value = SearchUiState.Loading

        // 1. Query local Room database first for instant offline-first results
        val localResponse = withContext(Dispatchers.IO) {
            appDb?.let { db ->
                try {
                    val messages = db.messageDao().searchMessagesOffline(workspaceId, query)
                    val tasks = db.taskDao().searchTasksOffline(workspaceId, query)
                    val notes = db.notesDao().searchNotesOffline(workspaceId, query)
                    val files = db.fileDao().searchFilesOffline(workspaceId, query)
                    val dms = if (currentUserId > 0) db.dmDao().searchDmsOffline(workspaceId, currentUserId, query) else emptyList()
                    WorkspaceSearchResponse(
                        query = query,
                        messages = messages,
                        directMessages = dms,
                        tasks = tasks,
                        notes = notes,
                        files = files
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }

        if (localResponse != null && !localResponse.isEmpty) {
            _state.value = SearchUiState.Results(localResponse)
        }

        // 2. Fetch server results in background and seamlessly update
        try {
            val remoteResults = api.searchWorkspace(workspaceId, query)
            _state.value = SearchUiState.Results(remoteResults)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (localResponse != null && !localResponse.isEmpty) {
                // Keep local results if network failed
                _state.value = SearchUiState.Results(localResponse)
            } else {
                _state.value = SearchUiState.Error("No matching cached items found for \"$query\".")
            }
        }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
        const val MAX_QUERY_LENGTH = 100
    }
}
