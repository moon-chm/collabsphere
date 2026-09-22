package com.collabsphere.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.dto.login.SearchUserResult
import com.collabsphere.app.model.UserRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserSearchViewModel(private val repo: UserRepo) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchUserResult>>(emptyList())
    val results: StateFlow<List<SearchUserResult>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(value: String) {
        _query.value = value
        searchJob?.cancel()

        val trimmed = value.trim()
        if (trimmed.length < 2) {
            _results.value = emptyList()
            _errorMessage.value = ""
            return
        }

        searchJob = viewModelScope.launch {
            delay(350) // debounce — avoids firing a request per keystroke
            _isSearching.value = true
            repo.searchUsers(trimmed)
                .onSuccess {
                    _results.value = it
                    _errorMessage.value = ""
                }
                .onFailure {
                    _errorMessage.value = it.message ?: "Search failed"
                }
            _isSearching.value = false
        }
    }
}
