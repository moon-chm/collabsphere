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

    // ── Reactions State — messageId → (emoji → count) ────────────────────────────
    private val _reactions = MutableStateFlow<Map<Int, Map<String, Int>>>(emptyMap())
    val reactions: StateFlow<Map<Int, Map<String, Int>>> = _reactions.asStateFlow()

    // ── Media upload progress ─────────────────────────────────────────────────────
    private val _isUploadingMedia = MutableStateFlow(false)
    val isUploadingMedia: StateFlow<Boolean> = _isUploadingMedia.asStateFlow()

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
                    "REACT_MESSAGE", "UNREACT_MESSAGE" -> {
                        val msgId = dto.id ?: return@collect
                        val incomingReactions = dto.reactions ?: return@collect
                        // Merge into our local reactions state map
                        val current = _reactions.value.toMutableMap()
                        current[msgId] = incomingReactions
                        _reactions.value = current
                    }
                    "READ_RECEIPT" -> {
                        // The sender of READ_RECEIPT is the one who read our messages
                        // Mark all messages sent by us to that person as read in local state
                        val readerId = dto.senderId
                        val updated = _messages.value.map { msg ->
                            if (msg.senderId == currentUserId && msg.receiverId == readerId) {
                                msg.copy(isRead = true)
                            } else msg
                        }
                        _messages.value = updated
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

        // Mark conversation as read when opened
        viewModelScope.launch {
            try {
                repo.markConversationRead(workspaceId, userId, chatPartnerId)
            } catch (_: Exception) {}
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
        onUserStoppedTyping(workspaceId, receiverId)
        viewModelScope.launch {
            try {
                repo.sendRealtimeDm(id, workspaceId, senderId, receiverId, content)
            } catch (e: Exception) {
                Log.e("DmViewModel", "Operation failed", e)
            }
        }
    }

    fun sendMediaMessage(
        baseUrl: String,
        workspaceId: Int,
        senderId: Int,
        receiverId: Int,
        fileBytes: ByteArray,
        mimeType: String,
        fileName: String
    ) {
        viewModelScope.launch {
            _isUploadingMedia.value = true
            try {
                repo.sendMediaDm(baseUrl, workspaceId, senderId, receiverId, fileBytes, mimeType, fileName)
            } catch (e: Exception) {
                Log.e("DmViewModel", "Media send failed", e)
            } finally {
                _isUploadingMedia.value = false
            }
        }
    }

    fun toggleReaction(messageId: Int, emoji: String, workspaceId: Int, receiverId: Int) {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            val myCurrentReactions = try {
                repo.getUserReactionsForMessage(messageId, userId)
            } catch (_: Exception) { emptyList() }

            val alreadyReacted = emoji in myCurrentReactions
            if (alreadyReacted) {
                repo.deleteReactionLocally(messageId, userId, emoji)
                repo.sendReaction(messageId, emoji, workspaceId, receiverId, isAdd = false)
                // Optimistic local state update
                val current = _reactions.value.toMutableMap()
                val msgReactions = current[messageId]?.toMutableMap() ?: mutableMapOf()
                val newCount = (msgReactions[emoji] ?: 1) - 1
                if (newCount <= 0) msgReactions.remove(emoji) else msgReactions[emoji] = newCount
                current[messageId] = msgReactions
                _reactions.value = current
            } else {
                repo.upsertReactionLocally(messageId, userId, emoji)
                repo.sendReaction(messageId, emoji, workspaceId, receiverId, isAdd = true)
                // Optimistic local state update
                val current = _reactions.value.toMutableMap()
                val msgReactions = current[messageId]?.toMutableMap() ?: mutableMapOf()
                msgReactions[emoji] = (msgReactions[emoji] ?: 0) + 1
                current[messageId] = msgReactions
                _reactions.value = current
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