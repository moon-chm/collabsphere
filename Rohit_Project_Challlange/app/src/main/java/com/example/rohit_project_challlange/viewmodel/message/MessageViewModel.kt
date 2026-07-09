package com.example.rohit_project_challlange.viewmodel.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rohit_project_challlange.model.message.MessageEntity
import com.example.rohit_project_challlange.model.message.MessageRepo
import com.example.rohit_project_challlange.model.message.MessageStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MessageViewModel(
    private val repo: MessageRepo,
    private val loggedUserId: Int,
    private val loggedWorkspaceId: Int,
    private val loggedChannelId: Int,
    private val loggedUserName: String
) : ViewModel() {

    val currentUserId: Int = loggedUserId

    init {
        viewModelScope.launch {
            repo.startDeltaSyncLoop(loggedWorkspaceId, loggedChannelId)
        }
    }

    val messages: StateFlow<List<MessageEntity>> =
        repo.getMessage(loggedWorkspaceId, loggedChannelId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _messageContent = MutableStateFlow("")
    val messageContent = _messageContent.asStateFlow()

    fun onMessageContentChange(content: String) {
        _messageContent.value = content
    }

    fun onSendMessageUser() {
        val content = _messageContent.value.trim()

        if (content.isEmpty()) return

        viewModelScope.launch {
            val message = MessageEntity(
                userId = loggedUserId,
                workspaceId = loggedWorkspaceId,
                channelId = loggedChannelId,
                userName = loggedUserName,
                content = content,
                status = MessageStatus.Delivered
            )
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
}