package com.collabsphere.app.viewmodel.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.ChannelMessageCenter
import com.collabsphere.app.dto.message.ChannelReactionSummary
import com.collabsphere.app.model.DraftStore
import com.collabsphere.app.model.message.MessageEntity
import com.collabsphere.app.model.message.MessageRepo
import com.collabsphere.app.model.message.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class MessageViewModel(
    private val repo: MessageRepo,
    private val loggedUserId: Int,
    private val loggedWorkspaceId: Int,
    private val loggedChannelId: Int,
    private val loggedUserName: String,
    private val draftStore: DraftStore
) : ViewModel() {

    val currentUserId: Int = loggedUserId

    private val _reactions = MutableStateFlow<Map<Int, Map<String, Set<Int>>>>(emptyMap())
    val reactions = _reactions.asStateFlow()

    private val _typingUsers = MutableStateFlow<List<String>>(emptyList())
    val typingUsers = _typingUsers.asStateFlow()

    private val _pinned = MutableStateFlow<List<MessageEntity>>(emptyList())
    val pinned = _pinned.asStateFlow()

    private val draftKey = DraftStore.channelKey(loggedWorkspaceId, loggedChannelId)
    private var isEditingMessage = false
    private var draftBeforeEdit = ""

    private val typingUntil = mutableMapOf<Int, Pair<String, Long>>()
    private var isTypingSent = false
    private var lastTypingSentAt = 0L

    init {
        viewModelScope.launch {
            repo.startDeltaSyncLoop(loggedWorkspaceId, loggedChannelId)
        }
        viewModelScope.launch {
            refreshReactions()
        }
        viewModelScope.launch {
            refreshPinned()
        }
        viewModelScope.launch {
            ChannelMessageCenter.incoming
                .filter { it.workspaceId == loggedWorkspaceId && it.channelId == loggedChannelId }
                .collect { change ->
                    val known = _pinned.value.firstOrNull { it.id == change.id }
                    val nowPinned = change.pinnedAt != null && !change.isDeleted
                    if ((known != null) != nowPinned || (known != null && known.pinnedAt != change.pinnedAt)) {
                        refreshPinned()
                    }
                }
        }
        viewModelScope.launch {
            ChannelMessageCenter.reactions
                .filter { it.workspaceId == loggedWorkspaceId && it.channelId == loggedChannelId }
                .collect { applyReactionSummary(it) }
        }
        viewModelScope.launch {
            ChannelMessageCenter.typing
                .filter { it.workspaceId == loggedWorkspaceId && it.channelId == loggedChannelId && it.userId != loggedUserId }
                .collect { event ->
                    if (event.isTyping) {
                        typingUntil[event.userId] = event.userName to System.currentTimeMillis() + TYPING_VISIBLE_MS
                    } else {
                        typingUntil.remove(event.userId)
                    }
                    publishTypingUsers()
                }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                if (typingUntil.values.any { it.second <= now }) {
                    typingUntil.entries.removeAll { it.value.second <= now }
                    publishTypingUsers()
                }
            }
        }
    }

    private fun publishTypingUsers() {
        _typingUsers.value = typingUntil.values.map { it.first }.sorted()
    }

    private fun applyReactionSummary(summary: ChannelReactionSummary) {
        val cleaned = summary.reactors
            .mapValues { it.value.toSet() }
            .filterValues { it.isNotEmpty() }
        _reactions.update { current ->
            if (cleaned.isEmpty()) current - summary.messageId else current + (summary.messageId to cleaned)
        }
    }

    private suspend fun refreshReactions() {
        repo.fetchReactions(loggedWorkspaceId, loggedChannelId).onSuccess { summaries ->
            _reactions.value = summaries
                .associate { summary -> summary.messageId to summary.reactors.mapValues { it.value.toSet() } }
                .filterValues { it.isNotEmpty() }
        }
    }

    private suspend fun refreshPinned() {
        repo.fetchPinned(loggedWorkspaceId, loggedChannelId).onSuccess { _pinned.value = it }
    }

    fun togglePin(message: MessageEntity) {
        if (message.id <= 0) return
        val pin = message.pinnedAt == null && _pinned.value.none { it.id == message.id }
        viewModelScope.launch {
            repo.setPinned(message.id, pin)
                .onSuccess { refreshPinned() }
                .onFailure { _uiMessages.tryEmit("Couldn't update the pin. Check your connection.") }
        }
    }

    fun beginEdit(originalText: String) {
        if (!isEditingMessage) {
            draftBeforeEdit = _messageContent.value
            isEditingMessage = true
        }
        _messageContent.value = originalText
    }

    fun endEdit() {
        isEditingMessage = false
        _messageContent.value = draftBeforeEdit
        draftBeforeEdit = ""
    }

    fun toggleReaction(messageId: Int, emoji: String) {
        if (messageId <= 0) return
        val alreadyReacted = _reactions.value[messageId]?.get(emoji)?.contains(loggedUserId) == true
        _reactions.update { current ->
            val forMessage = current[messageId].orEmpty().toMutableMap()
            val users = forMessage[emoji].orEmpty().let { if (alreadyReacted) it - loggedUserId else it + loggedUserId }
            if (users.isEmpty()) forMessage.remove(emoji) else forMessage[emoji] = users
            if (forMessage.isEmpty()) current - messageId else current + (messageId to forMessage)
        }
        viewModelScope.launch {
            repo.toggleReaction(messageId, emoji, add = !alreadyReacted)
                .onSuccess { applyReactionSummary(it) }
                .onFailure { refreshReactions() }
        }
    }

    private fun updateTypingState(hasText: Boolean) {
        val now = System.currentTimeMillis()
        if (hasText) {
            if (!isTypingSent || now - lastTypingSentAt >= TYPING_RESEND_MS) {
                isTypingSent = true
                lastTypingSentAt = now
                viewModelScope.launch { repo.sendTyping(loggedWorkspaceId, loggedChannelId, true) }
            }
        } else if (isTypingSent) {
            isTypingSent = false
            viewModelScope.launch { repo.sendTyping(loggedWorkspaceId, loggedChannelId, false) }
        }
    }

    override fun onCleared() {
        val finalDraft = if (isEditingMessage) draftBeforeEdit else _messageContent.value
        CoroutineScope(Dispatchers.IO).launch { draftStore.save(draftKey, finalDraft) }
        if (isTypingSent) {
            isTypingSent = false
            CoroutineScope(Dispatchers.IO).launch { repo.sendTyping(loggedWorkspaceId, loggedChannelId, false) }
        }
        super.onCleared()
    }

    val messages: StateFlow<List<MessageEntity>?> =
        repo.getMessage(loggedWorkspaceId, loggedChannelId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder = _isLoadingOlder.asStateFlow()

    private val _hasMoreOlder = MutableStateFlow(true)
    val hasMoreOlder = _hasMoreOlder.asStateFlow()

    fun loadOlder() {
        if (_isLoadingOlder.value || !_hasMoreOlder.value) return
        _isLoadingOlder.value = true
        viewModelScope.launch {
            repo.loadOlderMessages(loggedWorkspaceId, loggedChannelId)
                .onSuccess { hasMore ->
                    _hasMoreOlder.value = hasMore
                    refreshReactions()
                }
            _isLoadingOlder.value = false
        }
    }

    private val _replyingTo = MutableStateFlow<MessageEntity?>(null)
    val replyingTo = _replyingTo.asStateFlow()

    fun startReply(message: MessageEntity) {
        if (message.id > 0) _replyingTo.value = message
    }

    fun cancelReply() {
        _replyingTo.value = null
    }

    private val _messageContent = MutableStateFlow("")
    val messageContent = _messageContent.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = draftStore.load(draftKey)
            if (saved.isNotBlank() && _messageContent.value.isEmpty()) {
                _messageContent.value = saved
            }
            _messageContent
                .drop(1)
                .debounce(DRAFT_SAVE_DEBOUNCE_MS)
                .collect { if (!isEditingMessage) draftStore.save(draftKey, it) }
        }
    }

    private val _isUploadingMedia = MutableStateFlow(false)
    val isUploadingMedia = _isUploadingMedia.asStateFlow()

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val uiMessages = _uiMessages.asSharedFlow()

    fun onSendMedia(fileBytes: ByteArray, mimeType: String, fileName: String) {
        if (_isUploadingMedia.value) return
        if (fileBytes.size > MAX_MEDIA_BYTES) {
            _uiMessages.tryEmit("Images must be 10 MB or smaller.")
            return
        }
        _isUploadingMedia.value = true
        val caption = _messageContent.value.trim()
        val replyToId = _replyingTo.value?.id?.takeIf { it > 0 }
        viewModelScope.launch {
            val message = MessageEntity(
                userId = loggedUserId,
                workspaceId = loggedWorkspaceId,
                channelId = loggedChannelId,
                userName = loggedUserName,
                content = caption,
                status = MessageStatus.Delivered,
                replyToId = replyToId
            )
            repo.sendMediaMessage(message, fileBytes, mimeType, fileName)
                .onSuccess {
                    if (_messageContent.value.trim() == caption) {
                        _messageContent.value = ""
                        updateTypingState(false)
                    }
                    if (_replyingTo.value?.id == replyToId) {
                        _replyingTo.value = null
                    }
                }
                .onFailure {
                    _uiMessages.tryEmit("Couldn't upload the image. Check your connection and try again.")
                }
            _isUploadingMedia.value = false
        }
    }

    fun onMessageContentChange(content: String) {
        _messageContent.value = content
        updateTypingState(content.isNotBlank())
    }

    fun onSendMessageUser() {
        val content = _messageContent.value.trim()

        if (content.isEmpty()) return
        updateTypingState(false)

        viewModelScope.launch {
            val message = MessageEntity(
                userId = loggedUserId,
                workspaceId = loggedWorkspaceId,
                channelId = loggedChannelId,
                userName = loggedUserName,
                content = content,
                status = MessageStatus.Delivered,
                replyToId = _replyingTo.value?.id?.takeIf { it > 0 }
            )
            _replyingTo.value = null
            repo.sendMessageToUser(message)
            _messageContent.value = ""
        }
    }

    fun changeStatus(message: MessageEntity, newmsgStatus: MessageStatus) {
        viewModelScope.launch {
            repo.updateMessage(message.copy(status = newmsgStatus))
        }
    }

    fun onDeleteMessage(messageId: Int) {
        viewModelScope.launch {
            repo.deleteMessage(messageId, loggedUserId, loggedWorkspaceId, loggedChannelId)
        }
    }

    companion object {
        private const val TYPING_RESEND_MS = 3_000L
        private const val TYPING_VISIBLE_MS = 6_000L
        private const val MAX_MEDIA_BYTES = 10 * 1024 * 1024
        private const val DRAFT_SAVE_DEBOUNCE_MS = 400L
    }
}
