package com.collabsphere.app

import kotlinx.coroutines.withTimeoutOrNull
import com.collabsphere.app.remote.login.LoginApiService
import androidx.work.WorkManager
import android.util.Log
import android.content.Context
import android.content.Intent
import com.collabsphere.app.model.AppDatabase
import com.collabsphere.app.model.BackgroundSyncRegistry
import com.collabsphere.app.remote.dm.DmWebSocketService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Centralizes what "log out" actually has to tear down: the previous user's cached data must not
 * remain readable on a shared device, and their background DM connection must not keep running
 * (it can no longer authenticate once the token is cleared, but the foreground service/notification
 * would otherwise linger until the process dies).
 */
class SessionManager(
    private val context: Context,
    private val appDatabase: AppDatabase,
    private val userPreferences: UserPreferences,
    private val workManager: WorkManager,
    private val loginApiService: LoginApiService
) {
    /** Clear the previous account's jobs, tokens, and Room rows before accepting a different user. */
    suspend fun prepareForAuthenticatedUser(userId: Int) {
        val existingUserId = userPreferences.userIdFlow.first()
        if (existingUserId != -1 && existingUserId != userId) logout()
    }

    suspend fun logout() {
        // Best-effort and bounded: logging out must still work offline or against a cold-starting
        // server. It has to happen first, while the session token can still authenticate the call.
        try {
            withTimeoutOrNull(FCM_UNREGISTER_TIMEOUT_MS) { loginApiService.clearFcmToken() }
        } catch (e: Exception) {
            Log.w("SessionManager", "Couldn't unregister push token on logout", e)
        }
        // Queued offline edits read the auth token when they run — left in place they would be sent
        // under whoever logs in next on this device.
        try {
            withContext(Dispatchers.IO) {
                workManager.cancelAllWork().result.get(WORK_CANCELLATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
        } catch (e: Exception) {
            Log.w("SessionManager", "Couldn't confirm cancellation of queued work before logout", e)
        }
        BackgroundSyncRegistry.cancelAllAndJoin()
        context.stopService(Intent(context, DmWebSocketService::class.java))
        withContext(Dispatchers.IO) {
            appDatabase.clearAllTables()
        }
        userPreferences.clearPreferences()
    }

    private companion object {
        const val FCM_UNREGISTER_TIMEOUT_MS = 5_000L
        const val WORK_CANCELLATION_TIMEOUT_MS = 5_000L
    }
}
