package com.collabsphere.app.viewmodel.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.model.channels.ChannelDeleteResult
import com.collabsphere.app.model.channels.ChannelEntity
import com.collabsphere.app.model.channels.ChannelRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.collabsphere.app.ChannelMessageCenter
import com.collabsphere.app.remote.message.MessageApiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ChannelViewModel(
    private val repo: ChannelRepo,
    private val loggeduserID: Int,
    private val loggedWorkspaceId: Int
) : ViewModel(), KoinComponent {

    private val messageApi: MessageApiService by inject()

    /** Unread messages by others, per channel id (channels with none are absent). */
    private val _unreadCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val unreadCounts = _unreadCounts.asStateFlow()
    private var unreadRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            repo.startDeltaSyncLoop(loggedWorkspaceId)
        }
        // New, edited or deleted channel messages change the counts; batch bursts into one request.
        viewModelScope.launch {
            ChannelMessageCenter.incoming.collect { message ->
                if (message.workspaceId == loggedWorkspaceId && message.userId != loggeduserID) {
                    refreshUnreadCounts(debounceMs = UNREAD_REFRESH_DEBOUNCE_MS)
                }
            }
        }
    }

    /** Opening a channel reads it: drop its badge right away instead of waiting for the server. */
    fun onChannelOpened(channelId: Int) {
        _unreadCounts.value = _unreadCounts.value - channelId
    }

    /** Re-reads the badges; the screen calls this whenever it comes back into view. */
    fun refreshUnreadCounts(debounceMs: Long = 0L) {
        unreadRefreshJob?.cancel()
        unreadRefreshJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            // Offline or failed: keep the last known badges rather than clearing them.
            runCatching { messageApi.getUnreadCounts(loggedWorkspaceId) }
                .onSuccess { rows -> _unreadCounts.value = rows.associate { it.channelId to it.count } }
        }
    }

    val userChannels: StateFlow<List<ChannelEntity>?> = repo
        .getallchannelbyuser(loggedWorkspaceId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _channelName = MutableStateFlow("")
    val channelName = _channelName.asStateFlow()

    private val _description = MutableStateFlow("")
    val description = _description.asStateFlow()

    private val _channelStatus = MutableStateFlow<String?>(null)
    val channelStatus = _channelStatus.asStateFlow()

    private val _isCreatingChannel = MutableStateFlow(false)
    val isCreatingChannel = _isCreatingChannel.asStateFlow()
    private val _deletingChannelKeys = MutableStateFlow<Set<String>>(emptySet())
    val deletingChannelKeys = _deletingChannelKeys.asStateFlow()

    fun onChannelNamechange(name: String) {
        _channelName.value = name
    }

    fun onChannelDescriptionChange(desc: String) {
        _description.value = desc
    }

    fun clearStatus() {
        _channelStatus.value = null
    }

    fun onCreateChannel() {
        if (_isCreatingChannel.value) return
        val name = _channelName.value.trim()
        val desc = _description.value.trim()

        if (name.isEmpty()) {
            _channelStatus.value = "Channel name cannot be empty"
            return
        }

        _isCreatingChannel.value = true
        viewModelScope.launch {
            try {
                val fallbackEntity = ChannelEntity(
                    userId = loggeduserID,
                    workspaceId = loggedWorkspaceId,
                    channelName = name,
                    description = desc
                )

                val result = repo.addchanneltoscreen(
                    workspaceId = loggedWorkspaceId,
                    channelName = name,
                    userId = loggeduserID,
                    description = desc,
                    fallbackEntity = fallbackEntity
                )

                if (result.isSuccess) {
                    _channelStatus.value = "Channel #$name created successfully"
                    _channelName.value = ""
                    _description.value = ""
                } else {
                    val msg = result.exceptionOrNull()?.localizedMessage ?: ""
                    if (!msg.contains("Parent job", ignoreCase = true) && !msg.contains("cancel", ignoreCase = true)) {
                        _channelStatus.value = msg.ifBlank { "Failed to create channel" }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: ""
                if (!msg.contains("Parent job", ignoreCase = true) && !msg.contains("cancel", ignoreCase = true)) {
                    _channelStatus.value = msg.ifBlank { "Failed to create channel" }
                }
            } finally {
                _isCreatingChannel.value = false
            }
        }
    }

    fun onDeleteChannel(channel: ChannelEntity) {
        val key = "${channel.workspaceId}:${channel.channelName.lowercase()}:${channel.userId}"
        if (key in _deletingChannelKeys.value) return
        _deletingChannelKeys.value = _deletingChannelKeys.value + key
        viewModelScope.launch {
            try {
                val result = repo.deletechanneltoscreen(
                    channelName = channel.channelName,
                    workspaceId = loggedWorkspaceId,
                    userId = channel.userId
                )
                _channelStatus.value = when (result) {
                    ChannelDeleteResult.DELETED -> "Channel #${channel.channelName} deleted"
                    ChannelDeleteResult.FORBIDDEN -> "Only workspace admins or the channel's creator can delete #${channel.channelName}"
                    ChannelDeleteResult.FAILED -> "Failed to delete channel"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: ""
                if (!msg.contains("Parent job", ignoreCase = true) && !msg.contains("cancel", ignoreCase = true)) {
                    _channelStatus.value = msg.ifBlank { "Failed to delete channel" }
                }
            } finally {
                _deletingChannelKeys.value = _deletingChannelKeys.value - key
            }
        }
    }

    private companion object {
        const val UNREAD_REFRESH_DEBOUNCE_MS = 600L
    }
}
