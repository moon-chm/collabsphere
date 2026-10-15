package com.collabsphere.app.model.message

import com.collabsphere.app.dto.message.SavedMessageItem
import com.collabsphere.app.remote.message.MessageApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SavedMessagesStore(private val api: MessageApiService) {

    private val _items = MutableStateFlow<List<SavedMessageItem>>(emptyList())
    val items: StateFlow<List<SavedMessageItem>> = _items.asStateFlow()

    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun refresh(): Result<Unit> = runCatching {
        val list = api.getSaved()
        _items.value = list
        _keys.value = list.map { key(it.kind, it.messageId) }.toSet()
        _loaded.value = true
    }

    suspend fun toggle(kind: String, messageId: Int): Result<Boolean> {
        val k = key(kind, messageId)
        val saving = k !in _keys.value
        _keys.value = if (saving) _keys.value + k else _keys.value - k
        if (!saving) _items.value = _items.value.filterNot { key(it.kind, it.messageId) == k }
        return runCatching {
            if (saving) api.saveMessage(kind, messageId) else api.unsaveMessage(kind, messageId)
            if (saving) refresh()
            saving
        }.onFailure {
            _keys.value = if (saving) _keys.value - k else _keys.value + k
        }
    }

    fun clear() {
        _items.value = emptyList()
        _keys.value = emptySet()
        _loaded.value = false
    }

    companion object {
        const val CHANNEL = "CHANNEL"
        const val DM = "DM"
        fun key(kind: String, messageId: Int) = "$kind:$messageId"
    }
}
