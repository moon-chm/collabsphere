package com.example.rohit_project_challlange.viewmodel.dm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rohit_project_challlange.AppConfig
import com.example.rohit_project_challlange.NotificationHelper
import com.example.rohit_project_challlange.model.UserEntity
import com.example.rohit_project_challlange.model.dm.DmEntity
import com.example.rohit_project_challlange.model.dm.DmRepo
import com.example.rohit_project_challlange.model.workspace.WorkspaceRepo
import com.example.rohit_project_challlange.remote.dm.DmWebSocketService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class DmViewModel(
    private val repo: DmRepo,
    private val workspaceRepo: WorkspaceRepo,
    private val notificationHelper: NotificationHelper,
    private val context: Context
) : ViewModel() {

    private val _workspaceMembers = MutableStateFlow<List<UserEntity>>(emptyList())
    val workspaceMembers: StateFlow<List<UserEntity>> = _workspaceMembers.asStateFlow()

    private val _messages = MutableStateFlow<List<DmEntity>>(emptyList())
    val messages: StateFlow<List<DmEntity>> = _messages.asStateFlow()

    // ── Real-time Presence & Typing States ───────────────────────────────────────
    private val _onlineUserIds = MutableStateFlow<Set<Int>>(emptySet())
    val onlineUserIds: StateFlow<Set<Int>> = _onlineUserIds.asStateFlow()

    private val _typingPartnerIds = MutableStateFlow<Set<Int>>(emptySet())
    val typingPartnerIds: StateFlow<Set<Int>> = _typingPartnerIds.asStateFlow()

    private var historyCollectionJob: Job? = null
    private var memberCollectionJob: Job? = null
    private var eventsCollectionJob: Job? = null
    private var activeChatPartnerId: Int? = null
    private var currentWorkspaceId: Int? = null
    private var currentUserId: Int? = null

    // Debounced typing timer for outgoing typing events
    private var outgoingTypingJob: Job? = null
    private var isCurrentlyTyping = false

    // Auto-expiry timers for incoming typing indicators
    private val incomingTypingTimers = ConcurrentHashMap<Int, Job>()

    fun initWebSocketConnection(baseUrl: String, userId: Long) {
        currentUserId = userId.toInt()

        val intent = Intent(context, DmWebSocketService::class.java).apply {
            putExtra("BASE_URL", baseUrl)
            putExtra("USER_ID", userId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        startObservingEvents()
    }

    private fun startObservingEvents() {
        eventsCollectionJob?.cancel()
        eventsCollectionJob = viewModelScope.launch {
            repo.incomingEvents.collect { dto ->
                when (dto.action) {
                    "USER_ONLINE" -> {
                        if (dto.senderId != 0 && dto.senderId != currentUserId) {
                            _onlineUserIds.value = _onlineUserIds.value + dto.senderId
                        }
                    }
                    "USER_OFFLINE" -> {
                        if (dto.senderId != 0) {
                            _onlineUserIds.value = _onlineUserIds.value - dto.senderId
                            _typingPartnerIds.value = _typingPartnerIds.value - dto.senderId
                        }
                    }
                    "TYPING_START" -> {
                        val sender = dto.senderId
                        if (sender != 0 && sender != currentUserId) {
                            _typingPartnerIds.value = _typingPartnerIds.value + sender
                            // Auto-expire after 4s in case TYPING_STOP was lost
                            incomingTypingTimers[sender]?.cancel()
                            incomingTypingTimers[sender] = viewModelScope.launch {
                                delay(4000)
                                _typingPartnerIds.value = _typingPartnerIds.value - sender
                            }
                        }
                    }
                    "TYPING_STOP" -> {
                        val sender = dto.senderId
                        if (sender != 0) {
                            incomingTypingTimers[sender]?.cancel()
                            _typingPartnerIds.value = _typingPartnerIds.value - sender
                        }
                    }
                }
            }
        }
    }

    fun loadWorkspaceMembers(workspaceId: Int, baseUrl: String = AppConfig.BASE_URL) {
        currentWorkspaceId = workspaceId
        memberCollectionJob?.cancel()
        memberCollectionJob = viewModelScope.launch {
            try {
                workspaceRepo.syncWorkspaceMembers(workspaceId)
            } catch (_: Exception) {}

            // Fetch initial presence list
            try {
                val initialOnline = repo.fetchOnlineUsers(baseUrl, workspaceId)
                _onlineUserIds.value = initialOnline.toSet()
            } catch (_: Exception) {}

            workspaceRepo.getWorkspaceMembersFlow(workspaceId)
                .catch { e ->
                    Log.e("DM_VM", "Failed reading members stream from room storage", e)
                    _workspaceMembers.value = emptyList()
                }
                .collect { members ->
                    _workspaceMembers.value = members
                }
        }
    }

    fun loadChatHistory(workspaceId: Int, userId: Int, chatPartnerId: Int, baseUrl: String) {
        currentWorkspaceId = workspaceId
        currentUserId = userId
        activeChatPartnerId = chatPartnerId

        val intent = Intent(context, DmWebSocketService::class.java).apply {
            putExtra("UPDATE_PARTNER_ID", chatPartnerId)
        }
        context.startService(intent)

        historyCollectionJob?.cancel()
        historyCollectionJob = viewModelScope.launch {
            repo.getDmHistory(workspaceId, userId, chatPartnerId)
                .catch { e -> Log.e("DM_VM", "History collection error", e) }
                .collect { history ->
                    _messages.value = history
                }
        }
    }

    // ── Outgoing Typing Status ───────────────────────────────────────────────────

    fun onUserTyping(workspaceId: Int, partnerId: Int) {
        val senderId = currentUserId ?: return
        outgoingTypingJob?.cancel()

        outgoingTypingJob = viewModelScope.launch {
            if (!isCurrentlyTyping) {
                isCurrentlyTyping = true
                repo.sendTypingStatus(workspaceId, senderId, partnerId, isTyping = true)
            }
            // If no keystrokes for 2.5s, signal stop
            delay(2500)
            isCurrentlyTyping = false
            repo.sendTypingStatus(workspaceId, senderId, partnerId, isTyping = false)
        }
    }

    fun onUserStoppedTyping(workspaceId: Int, partnerId: Int) {
        val senderId = currentUserId ?: return
        outgoingTypingJob?.cancel()
        if (isCurrentlyTyping) {
            isCurrentlyTyping = false
            viewModelScope.launch {
                repo.sendTypingStatus(workspaceId, senderId, partnerId, isTyping = false)
            }
        }
    }

    fun closeChatSessionUi() {
        val activePartner = activeChatPartnerId
        val wsId = currentWorkspaceId
        if (activePartner != null && wsId != null) {
            onUserStoppedTyping(wsId, activePartner)
        }

        historyCollectionJob?.cancel()
        historyCollectionJob = null
        activeChatPartnerId = null

        val intent = Intent(context, DmWebSocketService::class.java).apply {
            putExtra("UPDATE_PARTNER_ID", -1)
        }
        context.startService(intent)
    }

    fun shutdownWebSocketEntirely() {
        closeChatSessionUi()
        memberCollectionJob?.cancel()
        memberCollectionJob = null
        eventsCollectionJob?.cancel()
        eventsCollectionJob = null

        val intent = Intent(context, DmWebSocketService::class.java)
        context.stopService(intent)
    }

    fun sendMessage(id: Int, workspaceId: Int, senderId: Int, receiverId: Int, content: String) {
        // Immediately cancel typing indicator when message is sent
        onUserStoppedTyping(workspaceId, receiverId)

        viewModelScope.launch {
            try {
                repo.sendRealtimeDm(id, workspaceId, senderId, receiverId, content)
            } catch (e: Exception) {
                Log.e("DmViewModel", "Operation failed", e)
            }
        }
    }

    fun deleteMessage(dmId: Int, workspaceId: Int, partnerId: Int = 0) {
        if (dmId < 0) return
        viewModelScope.launch {
            repo.deleteDm(dmId, workspaceId, partnerId)
        }
    }

    fun updateMessage(dmId: Int, workspaceId: Int, receiverId: Int, newContent: String) {
        if (dmId < 0) return
        viewModelScope.launch {
            val senderId = currentUserId ?: 0
            repo.updateDm(dmId, workspaceId, senderId, receiverId, newContent)
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentWorkspaceId = null
        currentUserId = null
        _messages.value = emptyList()
        incomingTypingTimers.values.forEach { it.cancel() }
        incomingTypingTimers.clear()
        closeChatSessionUi()
    }
}