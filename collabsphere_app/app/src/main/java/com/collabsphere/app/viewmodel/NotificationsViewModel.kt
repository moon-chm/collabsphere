package com.collabsphere.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.NotificationCenter
import com.collabsphere.app.dto.notification.NotificationResponse
import com.collabsphere.app.model.InboxFilter
import com.collabsphere.app.model.NotificationInbox
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _filter = MutableStateFlow(InboxFilter.ALL)
    val filter: StateFlow<InboxFilter> = _filter.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val pendingRemovals = mutableMapOf<Int, NotificationResponse>()

    init {
        refreshCount()
        viewModelScope.launch {
            NotificationCenter.incoming.collect { incoming ->
                val isNew = _notifications.value.none { it.id == incoming.id }
                _notifications.value = NotificationInbox.mergeIncoming(_notifications.value, incoming)
                if (isNew && !incoming.isRead) _unreadCount.value += 1
            }
        }
    }

    fun setFilter(filter: InboxFilter) {
        _filter.value = filter
    }

    fun refreshCount() {
        viewModelScope.launch {
            repo.getCount().onSuccess { _unreadCount.value = it.unread }
        }
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _isLoading.value = true
            fetchFirstPage()
            _isLoading.value = false
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchFirstPage()
            _isRefreshing.value = false
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value || _isLoading.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            repo.getNotifications(offset = _notifications.value.size.toLong(), limit = PAGE_SIZE)
                .onSuccess { page ->
                    _notifications.value = NotificationInbox.appendPage(_notifications.value, page)
                    _hasMore.value = page.size >= PAGE_SIZE
                }
                .onFailure { _statusMessage.value = "Couldn't load older notifications" }
            _isLoadingMore.value = false
        }
    }

    private suspend fun fetchFirstPage() {
        repo.getNotifications(limit = PAGE_SIZE)
            .onSuccess { page ->
                _notifications.value = page.filterNot { it.id in pendingRemovals }
                _hasMore.value = page.size >= PAGE_SIZE
            }
            .onFailure { error ->
                val msg = error.message ?: ""
                if (!msg.contains("Parent job", ignoreCase = true) && !msg.contains("cancel", ignoreCase = true)) {
                    _statusMessage.value = msg.ifBlank { "Failed to load notifications" }
                }
            }
        repo.getCount().onSuccess { _unreadCount.value = it.unread }
    }

    fun clearStatus() {
        _statusMessage.value = ""
    }

    fun onMarkRead(ids: List<Int>) {
        val unread = ids.filter { id -> _notifications.value.any { it.id == id && !it.isRead } }
        if (unread.isEmpty()) return
        _notifications.value = _notifications.value.map { if (it.id in unread) it.copy(isRead = true) else it }
        _unreadCount.value = (_unreadCount.value - unread.size).coerceAtLeast(0)
        viewModelScope.launch {
            repo.markRead(unread).onFailure {
                _notifications.value = _notifications.value.map { if (it.id in unread) it.copy(isRead = false) else it }
                refreshCount()
            }
        }
    }

    fun onMarkAllRead() {
        val previous = _notifications.value
        val previousCount = _unreadCount.value
        _notifications.value = previous.map { it.copy(isRead = true) }
        _unreadCount.value = 0
        viewModelScope.launch {
            repo.markRead(null).onFailure {
                _notifications.value = previous
                _unreadCount.value = previousCount
                _statusMessage.value = "Couldn't mark everything as read"
            }
        }
    }

    fun removeWithUndo(id: Int) {
        val item = _notifications.value.firstOrNull { it.id == id } ?: return
        pendingRemovals[id] = item
        _notifications.value = _notifications.value.filterNot { it.id == id }
        if (!item.isRead) _unreadCount.value = (_unreadCount.value - 1).coerceAtLeast(0)
    }

    fun undoRemove(id: Int) {
        val item = pendingRemovals.remove(id) ?: return
        _notifications.value = NotificationInbox.mergeIncoming(_notifications.value, item).sortedByDescending { it.createdAt }
        if (!item.isRead) _unreadCount.value += 1
    }

    fun commitRemove(id: Int) {
        if (!pendingRemovals.containsKey(id)) return
        viewModelScope.launch {
            repo.deleteOne(id)
                .onSuccess { pendingRemovals.remove(id) }
                .onFailure {
                    undoRemove(id)
                    _statusMessage.value = "Couldn't remove that notification"
                }
        }
    }

    fun commitAllPending() {
        pendingRemovals.keys.toList().forEach { commitRemove(it) }
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
            repo.clearAll()
                .onSuccess {
                    _notifications.value = emptyList()
                    _unreadCount.value = 0
                    _hasMore.value = false
                    pendingRemovals.clear()
                }
                .onFailure { _statusMessage.value = "Couldn't clear notifications" }
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
