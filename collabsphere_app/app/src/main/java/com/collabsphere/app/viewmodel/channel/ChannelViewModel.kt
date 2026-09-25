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

class ChannelViewModel(
    private val repo: ChannelRepo,
    private val loggeduserID: Int,
    private val loggedWorkspaceId: Int
) : ViewModel() {

    init {
        viewModelScope.launch {
            repo.startDeltaSyncLoop(loggedWorkspaceId)
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
        val name = _channelName.value.trim()
        val desc = _description.value.trim()

        if (name.isEmpty()) {
            _channelStatus.value = "Channel name cannot be empty"
            return
        }

        viewModelScope.launch {
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
                _channelStatus.value = "Failed to create channel"
            }
        }
    }

    fun onDeleteChannel(channel: ChannelEntity) {
        viewModelScope.launch {
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
        }
    }
}