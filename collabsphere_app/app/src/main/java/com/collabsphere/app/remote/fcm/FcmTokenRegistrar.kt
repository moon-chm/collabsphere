package com.collabsphere.app.remote.fcm

import android.util.Log
import com.collabsphere.app.AuthTokenHolder
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.remote.login.LoginApiService
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object FcmTokenRegistrar : KoinComponent {
    private val loginApiService: LoginApiService by inject()
    private val userPreferences: UserPreferences by inject()
    private const val TAG = "FcmTokenRegistrar"

    suspend fun sendTokenToServer(userId: Int, fcmToken: String): Boolean {
        return try {
            // The endpoint is authenticated. On a cold start this can run before MyApplication's
            // async DataStore → AuthTokenHolder sync has landed, so load the token directly if needed.
            if (AuthTokenHolder.token == null) {
                AuthTokenHolder.token = userPreferences.authTokenFlow.first()
            }
            if (AuthTokenHolder.token == null) {
                Log.w(TAG, "No session token yet — skipping FCM registration for user $userId")
                return false
            }
            val success = loginApiService.updateFcmToken(userId, fcmToken)
            Log.d(TAG, "Token registration result for user $userId: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync token with server", e)
            false
        }
    }

    /**
     * Called on login or app start to ensure current device FCM token is registered with server.
     */
    fun syncCurrentToken(userId: Int) {
        if (userId <= 0) return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result ?: return@addOnCompleteListener
            Log.d(TAG, "Retrieved current FCM token: $token")
            com.collabsphere.app.MyApplication.appScope.launch {
                sendTokenToServer(userId, token)
            }
        }
    }
}
