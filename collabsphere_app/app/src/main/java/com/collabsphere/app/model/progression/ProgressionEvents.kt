package com.collabsphere.app.model.progression

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide nudge that something which may have earned Sphere Points just reached the server (a task
 * update, a note pin, the app returning to the foreground). [ProgressionRepo] listens and refreshes
 * the authoritative summary; senders never touch progression state themselves.
 */
object ProgressionEvents {
    private val _requests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    fun requestRefresh() {
        _requests.tryEmit(Unit)
    }
}
