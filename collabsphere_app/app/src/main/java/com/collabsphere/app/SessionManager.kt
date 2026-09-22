package com.collabsphere.app

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
    private val userPreferences: UserPreferences
) {
    suspend fun logout() {
        context.stopService(Intent(context, DmWebSocketService::class.java))
        withContext(Dispatchers.IO) {
            appDatabase.clearAllTables()
        }
        userPreferences.clearPreferences()
    }
}
