package com.collabsphere.app.model.workspace

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.collabsphere.app.remote.workspace.WorkspaceApiService
import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceOfflineDeleteQueueTest {
    @Test
    fun `deleting an unmapped offline workspace appends delete and removes its local placeholder`() = runBlocking {
        val queuedWork = slot<OneTimeWorkRequest>()
        val workManager = mockk<WorkManager>(relaxed = true)
        val workspaceDao = mockk<WorkspaceDao>(relaxed = true)
        val dataStore = mockk<DataStore<Preferences>>()
        every { dataStore.data } returns flowOf(emptyPreferences())
        val repo = WorkspaceRepo(
            workspaceDao = workspaceDao,
            workspaceApiService = mockk<WorkspaceApiService>(relaxed = true),
            workManager = workManager,
            dataStore = dataStore
        )

        val deleted = repo.deleteWorkspaceFromScreen(-1, "secret")

        assertEquals(1, deleted)
        coVerify(exactly = 1) { workspaceDao.deleteWorkspaceById(-1) }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                "WORKSPACE_SYNC_-1",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                capture(queuedWork)
            )
        }
        assertEquals("DELETE", queuedWork.captured.workSpec.input.getString("ACTION_TYPE"))
        assertEquals(-1, queuedWork.captured.workSpec.input.getInt("WORKSPACE_ID", 0))
        assertTrue(queuedWork.captured.workSpec.input.getString("WORKSPACE_PASSWORD") == "secret")
    }

    @Test
    fun `canonical workspace actions reuse its original temporary work chain key`() = runBlocking {
        val queuedWork = slot<OneTimeWorkRequest>()
        val workManager = mockk<WorkManager>(relaxed = true)
        val workspaceDao = mockk<WorkspaceDao>(relaxed = true)
        coEvery { workspaceDao.getUserIdByEmail("member@example.com") } returns 8
        val dataStore = mockk<DataStore<Preferences>>()
        every { dataStore.data } returns flowOf(
            mutablePreferencesOf(intPreferencesKey("workspace_queue_key_42") to -17)
        )
        val api = mockk<WorkspaceApiService>()
        coEvery { api.addMemberToWorkspace(42, any()) } throws java.io.IOException("offline")
        val repo = WorkspaceRepo(workspaceDao, api, workManager, dataStore)

        val result = repo.addMemberToWorkspaceByEmail(42, "Member@Example.com")

        assertTrue(result.isSuccess)
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                "WORKSPACE_SYNC_-17_member@example.com",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                capture(queuedWork)
            )
        }
        assertEquals("ADD_MEMBER", queuedWork.captured.workSpec.input.getString("ACTION_TYPE"))
        assertEquals(42, queuedWork.captured.workSpec.input.getInt("WORKSPACE_ID", 0))
    }
}
