package com.collabsphere.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.NotificationCenter
import com.collabsphere.app.dto.notification.NotificationResponse
import com.collabsphere.app.model.NotificationRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Held at the app-navigation level (like DashboardViewModel) so its unread count survives
 * navigation and stays live for the Dashboard badge, not just while the inbox screen is open.
 */
class NotificationsViewModel(private val repo: NotificationRepo) : ViewModel() {

    private val _notifications = MutableStateFlow<List<NotificationResponse>>(emptyList())
    val notifications: StateFlow<List<NotificationResponse>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    init {
        refreshCount()
        // Live push: bump the badge instantly and prepend the notification to the list
        // the moment it arrives over the WebSocket, without waiting for a manual refresh.
        viewModelScope.launch {
            NotificationCenter.incoming.collect { incoming ->
                _notifications.value = listOf(incoming) + _notifications.value
                _unreadCount.value += 1
            }
        }
    }

    fun refreshCount() {
        viewModelScope.launch {
            repo.getCount().onSuccess { _unreadCount.value = it.unread }
        }
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _isLoading.value = true
            repo.getNotifications()
                .onSuccess { _notifications.value = it }
                .onFailure { _statusMessage.value = it.message ?: "Failed to load notifications" }
            _isLoading.value = false
            refreshCount()
        }
    }

    fun clearStatus() {
        _statusMessage.value = ""
    }

    fun onMarkRead(ids: List<Int>) {
        viewModelScope.launch {
            repo.markRead(ids).onSuccess {
                _notifications.value = _notifications.value.map {
                    if (it.id in ids) it.copy(isRead = true) else it
                }
                refreshCount()
            }
        }
    }

    fun onMarkAllRead() {
        viewModelScope.launch {
            repo.markRead(null).onSuccess {
                _notifications.value = _notifications.value.map { it.copy(isRead = true) }
                _unreadCount.value = 0
            }
        }
    }

    fun onDelete(id: Int) {
        viewModelScope.launch {
            repo.deleteOne(id).onSuccess {
                val wasUnread = _notifications.value.any { it.id == id && !it.isRead }
                _notifications.value = _notifications.value.filterNot { it.id == id }
                if (wasUnread) _unreadCount.value = (_unreadCount.value - 1).coerceAtLeast(0)
            }
        }
    }

    fun onClearAll() {
        viewModelScope.launch {
            repo.clearAll().onSuccess {
                _notifications.value = emptyList()
                _unreadCount.value = 0
            }
        }
    }
}
