package com.collabsphere.app.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar

data class QuietHours(
    val enabled: Boolean = false,
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60
) {
    fun isQuietAt(minuteOfDay: Int): Boolean {
        if (!enabled || startMinute == endMinute) return false
        return if (startMinute < endMinute) {
            minuteOfDay in startMinute until endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }
}

class QuietHoursStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<QuietHours> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            QuietHours(
                enabled = prefs[ENABLED] ?: false,
                startMinute = prefs[START] ?: QuietHours().startMinute,
                endMinute = prefs[END] ?: QuietHours().endMinute
            )
        }

    @Volatile
    private var cached = QuietHours()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            settings.collect { cached = it }
        }
    }

    fun isQuietNow(): Boolean {
        val now = Calendar.getInstance()
        return cached.isQuietAt(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
    }

    suspend fun update(settings: QuietHours) {
        dataStore.edit { prefs ->
            prefs[ENABLED] = settings.enabled
            prefs[START] = settings.startMinute
            prefs[END] = settings.endMinute
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val START = intPreferencesKey("quiet_hours_start_minute")
        val END = intPreferencesKey("quiet_hours_end_minute")
    }
}
