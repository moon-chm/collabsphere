package com.collabsphere.app.model.workspace

import com.collabsphere.app.dto.workspace.MemberResponse
import com.collabsphere.app.model.Cached
import com.collabsphere.app.model.SwrCache
import com.collabsphere.app.model.SyncSignals
import com.collabsphere.app.remote.workspace.WorkspaceApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.builtins.ListSerializer
import java.util.concurrent.ConcurrentHashMap

class MemberDirectory(
    private val api: WorkspaceApiService,
    private val cache: SwrCache
) {
    private data class Entry(val cached: Cached<List<MemberResponse>>, val signalVersion: Long)

    private val memory = ConcurrentHashMap<Int, Entry>()
    private val serializer = ListSerializer(MemberResponse.serializer())

    fun observe(workspaceId: Int, forceRefresh: Boolean = false): Flow<List<MemberResponse>> = flow {
        val known = memory[workspaceId] ?: cache.read(key(workspaceId), serializer)
            ?.let { Entry(it, signalVersion = -1) }
            ?.also { memory[workspaceId] = it }
        if (known != null) emit(known.cached.value)
        if (forceRefresh || isStale(workspaceId, known)) {
            val fresh = runCatching { fetch(workspaceId) }
            fresh.onSuccess { emit(it) }
            if (fresh.isFailure && known == null) throw fresh.exceptionOrNull()!!
        }
    }

    suspend fun refresh(workspaceId: Int): List<MemberResponse> = fetch(workspaceId)

    private suspend fun fetch(workspaceId: Int): List<MemberResponse> {
        val version = SyncSignals.version(workspaceId, SyncSignals.SCOPE_WORKSPACE)
        val list = api.getWorkspaceMembers(workspaceId)
        val cached = Cached(list, System.currentTimeMillis())
        memory[workspaceId] = Entry(cached, version)
        cache.write(key(workspaceId), serializer, list, cached.savedAt)
        return list
    }

    private fun isStale(workspaceId: Int, entry: Entry?): Boolean =
        entry == null ||
            entry.cached.ageMs() > FRESH_FOR_MS ||
            entry.signalVersion != SyncSignals.version(workspaceId, SyncSignals.SCOPE_WORKSPACE)

    fun clearMemory() = memory.clear()

    private fun key(workspaceId: Int) = "members_$workspaceId"

    companion object {
        const val FRESH_FOR_MS = 2 * 60_000L
    }
}
