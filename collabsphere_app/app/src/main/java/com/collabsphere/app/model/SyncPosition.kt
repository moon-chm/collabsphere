package com.collabsphere.app.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.collabsphere.app.remote.SyncPage
import kotlinx.coroutines.flow.first

/** Where a delta-sync loop resumes: the server's cursor once it has issued one, plus the legacy watermark. */
data class SyncPosition(val since: Long, val cursor: Long?)

/** The cursor is stored next to each loop's existing `*_last_sync_time*` key, so both clear together on logout. */
private fun cursorKey(sinceKey: Preferences.Key<Long>) = longPreferencesKey("${sinceKey.name}_cursor")

suspend fun DataStore<Preferences>.readSyncPosition(sinceKey: Preferences.Key<Long>): SyncPosition {
    val preferences = data.first()
    return SyncPosition(since = preferences[sinceKey] ?: 0L, cursor = preferences[cursorKey(sinceKey)])
}

/**
 * Records that [page] has been applied. Call only *after* its rows are stored locally — committing
 * first and crashing in between would skip those rows for good.
 *
 * On a reset both resume points are dropped, so the next poll starts over with a full sync.
 *
 * An empty page from a client that already holds a cursor writes nothing: staying on the older cursor
 * is still correct (it only means a little re-delivered overlap), and it spares a disk write on every
 * poll of a quiet workspace. [force] writes regardless (e.g. to record that an initial load happened).
 */
suspend fun DataStore<Preferences>.commitSyncPosition(
    sinceKey: Preferences.Key<Long>,
    current: SyncPosition,
    page: SyncPage<*>,
    newestUpdatedAt: Long?,
    force: Boolean = false
) {
    val firstCursor = current.cursor == null && page.cursor != null
    if (!force && !page.reset && page.items.isEmpty() && !firstCursor) return
    edit { preferences ->
        if (page.reset) {
            preferences.remove(sinceKey)
            preferences.remove(cursorKey(sinceKey))
            return@edit
        }
        if (newestUpdatedAt != null && newestUpdatedAt > (preferences[sinceKey] ?: 0L)) {
            preferences[sinceKey] = newestUpdatedAt
        }
        page.cursor?.let { preferences[cursorKey(sinceKey)] = it }
    }
}
