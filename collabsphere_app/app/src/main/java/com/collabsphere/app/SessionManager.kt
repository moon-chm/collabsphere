package com.collabsphere.app

import kotlinx.coroutines.withTimeoutOrNull
import com.collabsphere.app.remote.login.LoginApiService
import androidx.work.WorkManager
import android.util.Log
import android.content.Context
import android.content.Intent
import com.collabsphere.app.model.AppDatabase
import com.collabsphere.app.remote.dm.DmWebSocketService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        workManager.cancelAllWork()
        context.stopService(Intent(context, DmWebSocketService::class.java))
        withContext(Dispatchers.IO) {
            appDatabase.clearAllTables()
        }
        userPreferences.clearPreferences()
    }

    private companion object {
        const val FCM_UNREGISTER_TIMEOUT_MS = 5_000L
    }
}
