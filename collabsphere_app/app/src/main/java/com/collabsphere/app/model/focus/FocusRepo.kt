package com.collabsphere.app.model.focus

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.collabsphere.app.dto.focus.HoldMessageRequest
import com.collabsphere.app.remote.ApiStatusException
import com.collabsphere.app.remote.focus.FocusApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface HoldResult {
    data class Held(val sendAt: Long) : HoldResult
    data object NotFocusing : HoldResult
    data class Failed(val message: String) : HoldResult
}

sealed interface UrgentResult {
    data class Sent(val remainingToday: Int) : UrgentResult
    data class LimitReached(val message: String) : UrgentResult
    data class Failed(val message: String) : UrgentResult
}

enum class FocusSendMode { HOLD, NOW, URGENT }

class FocusRepo(
    private val api: FocusApiService,
    private val dataStore: DataStore<Preferences>
) {

    private val _myFocusUntil = MutableStateFlow<Long?>(null)
    val myFocusUntil: StateFlow<Long?> = _myFocusUntil.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val stored = dataStore.data
                .catch { emit(emptyPreferences()) }
                .map { it[MY_FOCUS_UNTIL] }
                .first()
            if (_myFocusUntil.value == null) _myFocusUntil.value = stored
        }
    }

    fun isFocusingNow(now: Long = System.currentTimeMillis()): Boolean = (_myFocusUntil.value ?: 0L) > now

    suspend fun refreshMine() {
        safely { api.getMyFocus() }?.let { remember(it.focusUntil) }
    }

    suspend fun startFocus(until: Long): Result<Long?> = runCatchingApi {
        api.setFocus(until).focusUntil.also { remember(it) }
    }

    suspend fun endFocus(): Result<Long?> = runCatchingApi {
        api.setFocus(null).focusUntil.also { remember(it) }
    }

    suspend fun focusOf(userId: Int, workspaceId: Int): Long? =
        safely { api.getFocus(userId, workspaceId) }?.focusUntil

    suspend fun hold(workspaceId: Int, receiverId: Int, content: String): HoldResult = try {
        HoldResult.Held(api.hold(HoldMessageRequest(workspaceId, receiverId, content)).sendAt)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiStatusException) {
        if (e.statusCode == 409) HoldResult.NotFocusing else HoldResult.Failed(e.serverMessage ?: "Couldn't hold the message")
    } catch (e: Exception) {
        HoldResult.Failed(OFFLINE_MESSAGE)
    }

    suspend fun sendUrgent(workspaceId: Int, receiverId: Int, content: String): UrgentResult = try {
        UrgentResult.Sent(api.urgent(HoldMessageRequest(workspaceId, receiverId, content)).remainingToday)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiStatusException) {
        if (e.statusCode == 429) UrgentResult.LimitReached(e.serverMessage ?: "No urgent messages left today")
        else UrgentResult.Failed(e.serverMessage ?: "Couldn't send the urgent message")
    } catch (e: Exception) {
        UrgentResult.Failed(OFFLINE_MESSAGE)
    }

    private suspend fun remember(until: Long?) {
        _myFocusUntil.value = until
        runCatching {
            dataStore.edit { prefs ->
                if (until == null) prefs.remove(MY_FOCUS_UNTIL) else prefs[MY_FOCUS_UNTIL] = until
            }
        }
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun <T> runCatchingApi(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        val MY_FOCUS_UNTIL = longPreferencesKey("my_focus_until")
        const val OFFLINE_MESSAGE = "Couldn't reach the server. Check your connection."
    }
}
