package com.collabsphere.app

import com.collabsphere.app.dto.notification.NotificationResponse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-scoped hub for real-time notification push frames arriving over the DM WebSocket
 * (server action = "NOTIFICATION", for MENTION/CHANNEL_MESSAGE/TASK_ASSIGNED/TASK_UPDATED).
 * Bridges [DmWebSocketService][com.collabsphere.app.remote.dm.DmWebSocketService]
 * (producer) to the notifications badge/list ViewModel (consumer) without a wider DI refactor.
 */
object NotificationCenter {
    private val _incoming = MutableSharedFlow<NotificationResponse>(extraBufferCapacity = 16)
    val incoming: SharedFlow<NotificationResponse> = _incoming.asSharedFlow()

    suspend fun push(notification: NotificationResponse) {
        _incoming.emit(notification)
    }
}
