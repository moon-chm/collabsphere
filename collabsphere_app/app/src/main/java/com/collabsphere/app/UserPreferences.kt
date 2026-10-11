package com.collabsphere.app

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences_ds",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(
                context,
                "user_prefs"
            )
        )
    }
)

class UserPreferences(
    private val dataStore: DataStore<Preferences>,
    private val encryptedTokenStore: EncryptedTokenStore
) {

    companion object {
        val USER_ID = intPreferencesKey("saved_user_id")
        val USER_NAME = stringPreferencesKey("saved_user_name")
        val USER_EMAIL = stringPreferencesKey("saved_user_email")
        val AUTH_TOKEN = stringPreferencesKey("saved_auth_token")
        val FCM_TOKEN = stringPreferencesKey("saved_fcm_token")
        val ONBOARDING_DONE = androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_done")
    }

    val fcmTokenFlow: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[FCM_TOKEN] }

    suspend fun saveFcmToken(token: String?) {
        dataStore.edit { preferences ->
            if (token.isNullOrBlank()) {
                preferences.remove(FCM_TOKEN)
            } else {
                preferences[FCM_TOKEN] = token
            }
        }
    }

    // Legacy plaintext DataStore flow — used only during the one-time migration in
    // migrateTokenToEncryptedStore(). Do not read this for normal authentication purposes.
    private val legacyAuthTokenFlow: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[AUTH_TOKEN] }

    /**
     * One-time migration: if a token exists in the legacy plaintext DataStore, move it to
     * EncryptedSharedPreferences and delete the plaintext copy. Safe to call on every launch;
     * it is a no-op if the encrypted store already has a token or the plaintext store is empty.
     *
     * Returns the migrated/existing token so the caller can hydrate AuthTokenHolder immediately.
     */
    suspend fun migrateTokenToEncryptedStore(): String? = withContext(Dispatchers.IO) {
        // Already have an encrypted token — nothing to do.
        val existing = encryptedTokenStore.readToken()
        if (!existing.isNullOrBlank()) return@withContext existing

        // Check if a plaintext token was left from a previous install.
        val legacy = legacyAuthTokenFlow.first()
        if (!legacy.isNullOrBlank()) {
            Log.i("UserPreferences", "Migrating token from DataStore to EncryptedSharedPreferences")
            encryptedTokenStore.writeToken(legacy)
            // Erase the plaintext copy only after the encrypted write succeeds.
            dataStore.edit { it.remove(AUTH_TOKEN) }
            return@withContext legacy
        }
        null
    }

    /**
     * Persist a new auth token securely and mirror it to the in-memory holder so the
     * Ktor request interceptor can attach it synchronously.
     */
    suspend fun saveAuthToken(token: String?) {
        withContext(Dispatchers.IO) {
            encryptedTokenStore.writeToken(token)
            // Belt-and-suspenders: ensure no plaintext copy remains.
            dataStore.edit { it.remove(AUTH_TOKEN) }
        }
        AuthTokenHolder.token = token
    }

    val userIdFlow: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[USER_ID] ?: -1
        }

    val userNameFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[USER_NAME] ?: ""
        }

    val userEmailFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[USER_EMAIL] ?: ""
        }

    val onboardingDoneFlow: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[ONBOARDING_DONE] ?: false }

    suspend fun markOnboardingDone() {
        dataStore.edit { it[ONBOARDING_DONE] = true }
    }

    suspend fun saveUserId(userId: Int) {
        dataStore.edit { preferences ->
            preferences[USER_ID] = userId
        }
    }

    suspend fun saveUserSession(userId: Int, userName: String, email: String) {
        dataStore.edit { preferences ->
            preferences[USER_ID] = userId
            preferences[USER_NAME] = userName
            preferences[USER_EMAIL] = email
        }
    }

    suspend fun updateUserName(userName: String) {
        dataStore.edit { preferences ->
            preferences[USER_NAME] = userName
        }
    }

    suspend fun updateUserEmail(email: String) {
        dataStore.edit { preferences ->
            preferences[USER_EMAIL] = email
        }
    }

    suspend fun clearPreferences() {
        // Clear encrypted credential first — prevents a window where the DataStore is cleared
        // but the encrypted token could still be used to authenticate.
        withContext(Dispatchers.IO) { encryptedTokenStore.clearAll() }
        dataStore.edit { preferences -> preferences.clear() }
        AuthTokenHolder.token = null
    }
}