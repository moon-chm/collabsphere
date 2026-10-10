package com.collabsphere.app.model.workspace

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.collabsphere.app.remote.workspace.WorkspaceApiService
import com.collabsphere.app.dto.workspace.WorkspaceRequest
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WorkspaceCreateRetryIdentityTest {
    @Test
    fun `ambiguous create failure queues the exact same operation ID sent to server`() = runBlocking {
        val clientRequestId = "create-operation-2026-01"
        val apiRequest = slot<WorkspaceRequest>()
        val queuedWork = slot<OneTimeWorkRequest>()
        val api = mockk<WorkspaceApiService>()
        val workManager = mockk<WorkManager>(relaxed = true)
        coEvery { api.createWorkspace(7, capture(apiRequest)) } throws IOException("response timed out")

        val repo = WorkspaceRepo(
            workspaceDao = mockk(relaxed = true),
            workspaceApiService = api,
            workManager = workManager,
            dataStore = mockk(relaxed = true)
        )

        val result = repo.addWorkspaceToScreen(
            WorkspaceEntity(
                id = 0,
                userId = 7,
                workspaceName = "Research",
                workspaceOwner = "Alex",
                workspacePassword = "secret"
            ),
            clientRequestId = clientRequestId
        )

        assertTrue(result.isSuccess)
        assertEquals(clientRequestId, apiRequest.captured.clientRequestId)
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                match { it.startsWith("WORKSPACE_SYNC_") },
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                capture(queuedWork)
            )
        }
        assertEquals(clientRequestId, queuedWork.captured.workSpec.input.getString("CLIENT_REQUEST_ID"))
    }
}
