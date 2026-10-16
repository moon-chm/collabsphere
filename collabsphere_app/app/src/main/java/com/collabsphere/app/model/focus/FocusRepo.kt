package com.collabsphere.app.model.focus

import com.collabsphere.app.dto.focus.HoldMessageRequest
import com.collabsphere.app.remote.ApiStatusException
import com.collabsphere.app.remote.focus.FocusApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HoldResult {
    data class Held(val sendAt: Long) : HoldResult
    data object NotFocusing : HoldResult
    data class Failed(val message: String) : HoldResult
}

class FocusRepo(private val api: FocusApiService) {

    private val _myFocusUntil = MutableStateFlow<Long?>(null)
    val myFocusUntil: StateFlow<Long?> = _myFocusUntil.asStateFlow()

    suspend fun refreshMine() {
        safely { api.getMyFocus() }?.let { _myFocusUntil.value = it.focusUntil }
    }

    suspend fun startFocus(until: Long): Result<Long?> = runCatchingApi {
        api.setFocus(until).focusUntil.also { _myFocusUntil.value = it }
    }

    suspend fun endFocus(): Result<Long?> = runCatchingApi {
        api.setFocus(null).focusUntil.also { _myFocusUntil.value = it }
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
        HoldResult.Failed("Couldn't reach the server. Check your connection.")
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
}
