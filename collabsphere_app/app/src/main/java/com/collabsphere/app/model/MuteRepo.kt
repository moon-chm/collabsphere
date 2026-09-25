package com.collabsphere.app.model

import com.collabsphere.app.dto.notification.MuteRequest
import com.collabsphere.app.dto.notification.MuteSetting
import com.collabsphere.app.remote.notification.NotificationApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MuteRepo(private val api: NotificationApiService) {

    private val _mutes = MutableStateFlow<Set<MuteSetting>>(emptySet())
    val mutes: StateFlow<Set<MuteSetting>> = _mutes.asStateFlow()

    suspend fun refresh() {
        runCatching { api.getMutes() }.onSuccess { _mutes.value = it.toSet() }
    }

    suspend fun setMuted(workspaceId: Int, channelId: Int?, muted: Boolean): Result<Unit> {
        val setting = MuteSetting(workspaceId, channelId?.takeIf { it > 0 })
        val previous = _mutes.value
        _mutes.value = if (muted) previous + setting else previous - setting
        return runCatching {
            check(api.setMute(MuteRequest(workspaceId, setting.channelId, muted))) { "Mute update rejected" }
        }.onFailure { _mutes.value = previous }
    }

    companion object {
        fun isWorkspaceMuted(mutes: Set<MuteSetting>, workspaceId: Int): Boolean =
            MuteSetting(workspaceId, null) in mutes

        fun isChannelMuted(mutes: Set<MuteSetting>, workspaceId: Int, channelId: Int): Boolean =
            MuteSetting(workspaceId, channelId) in mutes
    }
}
