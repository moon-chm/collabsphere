package com.collabsphere.app.model.workspace

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.work.WorkManager
import com.collabsphere.app.remote.workspace.WorkspaceApiService
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceIdResolutionTest {
    private class MemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }

        suspend fun setWorkspaceMapping(tempId: Int, realId: Int) {
            val key = intPreferencesKey("temp_ws_$tempId")
            updateData { mutablePreferencesOf(key to realId) }
        }
    }

    @Test
    fun `temporary workspace ID resolves after server mapping is saved`() = runTest {
        val store = MemoryPreferencesStore()
        val repo = WorkspaceRepo(
            mockk<WorkspaceDao>(relaxed = true),
            mockk<WorkspaceApiService>(relaxed = true),
            mockk<WorkManager>(relaxed = true),
            store
        )
        store.setWorkspaceMapping(tempId = -17, realId = 203)

        assertEquals(203, repo.observeCanonicalWorkspaceId(-17).first())
    }

    @Test
    fun `unmapped temporary ID remains usable as offline identity`() = runTest {
        val repo = WorkspaceRepo(
            mockk<WorkspaceDao>(relaxed = true),
            mockk<WorkspaceApiService>(relaxed = true),
            mockk<WorkManager>(relaxed = true),
            MemoryPreferencesStore()
        )

        assertEquals(-17, repo.observeCanonicalWorkspaceId(-17).first())
    }
}
