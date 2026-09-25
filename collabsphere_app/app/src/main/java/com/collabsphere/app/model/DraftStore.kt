package com.collabsphere.app.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

class DraftStore(private val dataStore: DataStore<Preferences>) {

    suspend fun load(key: String): String =
        runCatching { dataStore.data.first()[stringPreferencesKey(key)].orEmpty() }.getOrDefault("")

    suspend fun save(key: String, text: String) {
        runCatching {
            dataStore.edit { preferences ->
                val prefKey = stringPreferencesKey(key)
                if (text.isBlank()) preferences.remove(prefKey) else preferences[prefKey] = text
            }
        }
    }

    companion object {
        fun channelKey(workspaceId: Int, channelId: Int) = "draft_channel_${workspaceId}_$channelId"
        fun dmKey(workspaceId: Int, partnerId: Int) = "draft_dm_${workspaceId}_$partnerId"
    }
}
