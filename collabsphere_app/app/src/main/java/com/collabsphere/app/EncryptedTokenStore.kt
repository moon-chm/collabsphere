package com.collabsphere.app

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the current session's JWT in Android Keystore-backed EncryptedSharedPreferences.
 *
 * Why not DataStore?
 * - PreferencesDataStore is plaintext protobuf on disk — readable on rooted devices or via
 *   adb backup paths on API 31+ even when allowBackup=false.
 * - EncryptedSharedPreferences wraps the underlying file with AES-256 GCM keyed by a key
 *   that lives inside the Android Keystore hardware-backed enclave (on supporting devices).
 *
 * Thread safety: EncryptedSharedPreferences is backed by SharedPreferences, which is
 * process-thread-safe for individual get/put operations without additional locking.
 */
class EncryptedTokenStore(context: Context) {

    private val prefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences — falling back to null store", e)
            null
        }
    }

    /**
     * Read the stored token. Returns null if no token is stored or if the keystore is unavailable.
     */
    fun readToken(): String? = runCatching {
        prefs?.getString(KEY_AUTH_TOKEN, null)
    }.getOrElse {
        Log.e(TAG, "Failed to read token from encrypted store", it)
        null
    }

    /**
     * Write a token to the encrypted store. Safe to call with null to erase the stored credential.
     */
    fun writeToken(token: String?) {
        runCatching {
            prefs?.edit()?.apply {
                if (token.isNullOrBlank()) {
                    remove(KEY_AUTH_TOKEN)
                } else {
                    putString(KEY_AUTH_TOKEN, token)
                }
                apply()
            }
        }.onFailure {
            Log.e(TAG, "Failed to write token to encrypted store", it)
        }
    }

    /** Clear all credentials from the encrypted store (e.g., on logout). */
    fun clearAll() {
        runCatching {
            prefs?.edit()?.clear()?.apply()
        }.onFailure {
            Log.e(TAG, "Failed to clear encrypted store", it)
        }
    }

    companion object {
        private const val TAG = "EncryptedTokenStore"
        private const val PREFS_FILE_NAME = "collabsphere_secure_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
    }
}
