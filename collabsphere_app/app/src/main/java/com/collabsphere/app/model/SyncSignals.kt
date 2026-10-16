package com.collabsphere.app.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class WorkspaceSyncEvent(
    val action: String = "WORKSPACE_SYNC",
    val workspaceId: Int,
    val scopes: List<String> = emptyList()
)

object SyncSignals {
    const val SCOPE_TASKS = "tasks"
    const val SCOPE_NOTES = "notes"
    const val SCOPE_FILES = "files"
    const val SCOPE_CHANNELS = "channels"
    const val SCOPE_VOTES = "votes"
    const val SCOPE_WORKSPACE = "workspace"
    const val LIVE_FALLBACK_MS = 60_000L

    private val versions = ConcurrentHashMap<String, MutableStateFlow<Long>>()

    @Volatile
    var serverReady: Boolean = false
        private set

    @Volatile
    var isSocketConnected: () -> Boolean = { false }

    val isLive: Boolean get() = serverReady && isSocketConnected()

    private fun key(workspaceId: Int?, scope: String) = "${workspaceId ?: "*"}:$scope"

    private fun flowFor(workspaceId: Int?, scope: String) =
        versions.getOrPut(key(workspaceId, scope)) { MutableStateFlow(0L) }

    fun version(workspaceId: Int?, scope: String): Long = flowFor(workspaceId, scope).value

    fun onEvent(event: WorkspaceSyncEvent) {
        event.scopes.forEach { scope ->
            flowFor(event.workspaceId, scope).update { it + 1 }
            if (scope == SCOPE_WORKSPACE) flowFor(null, SCOPE_WORKSPACE).update { it + 1 }
        }
    }

    fun onReady() {
        serverReady = true
        wakeAll()
    }

    fun onDisconnected() {
        if (!serverReady) return
        serverReady = false
        wakeAll()
    }

    fun onAppForeground() {
        wakeAll()
    }

    private fun wakeAll() {
        versions.values.forEach { flow -> flow.update { it + 1 } }
    }

    fun waitTimeout(normalDelayMs: Long, lastSyncSucceeded: Boolean, live: Boolean = isLive): Long =
        if (live && lastSyncSucceeded) maxOf(normalDelayMs, LIVE_FALLBACK_MS) else normalDelayMs

    suspend fun awaitNext(workspaceId: Int?, scope: String, seenVersion: Long, normalDelayMs: Long, lastSyncSucceeded: Boolean) {
        val timeout = waitTimeout(normalDelayMs, lastSyncSucceeded)
        withTimeoutOrNull(timeout) { flowFor(workspaceId, scope).first { it > seenVersion } }
    }

    internal fun resetForTest() {
        versions.clear()
        serverReady = false
        isSocketConnected = { false }
    }
}
