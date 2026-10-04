package com.collabsphere.app.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.collabsphere.app.remote.SyncPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncPositionTest {

    /** In-memory DataStore that counts writes, so "nothing changed" can be asserted. */
    private class FakeDataStore : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        var writes = 0
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            writes++
            state.value = updated
            return updated
        }
    }

    private val key = longPreferencesKey("tasks_last_sync_time_7")

    @Test
    fun `fresh client stores the cursor and the newest timestamp`() = runTest {
        val store = FakeDataStore()
        val start = store.readSyncPosition(key)
        assertEquals(SyncPosition(since = 0L, cursor = null), start)

        store.commitSyncPosition(key, start, SyncPage(listOf("row"), cursor = 500L, reset = false), newestUpdatedAt = 1_000L)

        assertEquals(SyncPosition(since = 1_000L, cursor = 500L), store.readSyncPosition(key))
    }

    @Test
    fun `first cursor is stored even when the page is empty`() = runTest {
        val store = FakeDataStore()
        val start = store.readSyncPosition(key)
        store.commitSyncPosition(key, start, SyncPage(emptyList<String>(), cursor = 42L, reset = false), newestUpdatedAt = null)
        assertEquals(42L, store.readSyncPosition(key).cursor)
    }

    @Test
    fun `empty page with a cursor already held writes nothing`() = runTest {
        val store = FakeDataStore()
        store.commitSyncPosition(key, store.readSyncPosition(key), SyncPage(listOf("row"), 10L, false), 5L)
        val writesBefore = store.writes

        store.commitSyncPosition(key, store.readSyncPosition(key), SyncPage(emptyList<String>(), 11L, false), null)

        assertEquals(writesBefore, store.writes)
        assertEquals(10L, store.readSyncPosition(key).cursor)
    }

    @Test
    fun `older server without cursors keeps advancing the timestamp only`() = runTest {
        val store = FakeDataStore()
        store.commitSyncPosition(key, store.readSyncPosition(key), SyncPage(listOf("row"), cursor = null, reset = false), 2_000L)
        assertEquals(SyncPosition(since = 2_000L, cursor = null), store.readSyncPosition(key))
    }

    @Test
    fun `timestamp never moves backwards`() = runTest {
        val store = FakeDataStore()
        store.commitSyncPosition(key, store.readSyncPosition(key), SyncPage(listOf("a"), 1L, false), 9_000L)
        store.commitSyncPosition(key, store.readSyncPosition(key), SyncPage(listOf("b"), 2L, false), 3_000L)
        assertEquals(9_000L, store.readSyncPosition(key).since)
        assertEquals(2L, store.readSyncPosition(key).cursor)
    }

    @Test
    fun `reset drops both resume points so the next poll is a full sync`() = runTest {
        val store = FakeDataStore()
        store.commitSyncPosition(key, store.readSyncPosition(key), SyncPage(listOf("a"), 77L, false), 9_000L)

        store.commitSyncPosition(key, store.readSyncPosition(key), SyncPage(emptyList<String>(), 3L, reset = true), null)

        val after = store.readSyncPosition(key)
        assertEquals(0L, after.since)
        assertNull(after.cursor)
    }

    @Test
    fun `force writes an empty page, marking an initial load as done`() = runTest {
        val store = FakeDataStore()
        store.commitSyncPosition(key, SyncPosition(0L, null), SyncPage(emptyList<String>(), null, false), 1L, force = true)
        assertEquals(1L, store.readSyncPosition(key).since)
    }

    @Test
    fun `cursor keys are kept per sync loop`() = runTest {
        val store = FakeDataStore()
        val other = longPreferencesKey("notes_last_sync_time_7")
        store.commitSyncPosition(key, store.readSyncPosition(key), SyncPage(listOf("a"), 5L, false), 1L)
        assertNull(store.readSyncPosition(other).cursor)
    }
}
