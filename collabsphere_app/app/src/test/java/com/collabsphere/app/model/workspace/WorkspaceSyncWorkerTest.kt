package com.collabsphere.app.model.workspace

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.collabsphere.app.dto.workspace.AddMemberRequest
import com.collabsphere.app.dto.workspace.MemberResponse
import com.collabsphere.app.dto.workspace.WorkspaceRequest
import com.collabsphere.app.remote.ApiStatusException
import com.collabsphere.app.remote.workspace.WorkspaceApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test

class WorkspaceSyncWorkerTest {
    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.i(any(), any<String>(), any()) } returns 0
        every { Log.d(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }
    @Test
    fun `queued add-member action resolves even the negative one temp workspace ID`() = runBlocking {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.getInputData() } returns Data.Builder()
            .putString("ACTION_TYPE", "ADD_MEMBER")
            .putInt("WORKSPACE_ID", -1)
            .putString("EMAIL", "member@example.com")
            .build()

        val repo = mockk<WorkspaceRepo>()
        every { repo.observeCanonicalWorkspaceId(-1) } returns flowOf(42)
        val api = mockk<WorkspaceApiService>()
        coEvery {
            api.addMemberToWorkspace(42, AddMemberRequest("member@example.com"))
        } returns MemberResponse(42, 8, "Member", "member@example.com")

        val worker = WorkspaceSyncWorker(
            context = mockk<Context>(relaxed = true),
            workerParams = params,
            apiService = api,
            repo = repo
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `queued delete resolves temporary ID before calling server`() = runBlocking {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.getInputData() } returns Data.Builder()
            .putString("ACTION_TYPE", "DELETE")
            .putInt("WORKSPACE_ID", -17)
            .putString("WORKSPACE_PASSWORD", "secret")
            .build()
        val repo = mockk<WorkspaceRepo>()
        every { repo.observeCanonicalWorkspaceId(-17) } returns flowOf(42)
        val api = mockk<WorkspaceApiService>()
        coEvery { api.deleteWorkspaceFromServer(42, "secret") } returns listOf(42)
        val worker = WorkspaceSyncWorker(mockk<Context>(relaxed = true), params, api, repo)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `queued delete is a no-op after its temporary workspace create was permanently rejected`() = runBlocking {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.getInputData() } returns Data.Builder()
            .putString("ACTION_TYPE", "DELETE")
            .putInt("WORKSPACE_ID", -17)
            .putString("WORKSPACE_PASSWORD", "secret")
            .build()
        val repo = mockk<WorkspaceRepo>()
        every { repo.observeCanonicalWorkspaceId(-17) } returns flowOf(0)
        val worker = WorkspaceSyncWorker(
            mockk<Context>(relaxed = true),
            params,
            mockk<WorkspaceApiService>(relaxed = true),
            repo
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `permanent create refusal records a failed mapping for dependent queue actions`() = runBlocking {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.getInputData() } returns Data.Builder()
            .putString("ACTION_TYPE", "CREATE")
            .putInt("USER_ID", 7)
            .putInt("WORKSPACE_ID", -17)
            .putString("WORKSPACE_NAME", "Research")
            .putString("WORKSPACE_OWNER", "Alex")
            .putString("WORKSPACE_PASSWORD", "secret")
            .putString("CLIENT_REQUEST_ID", "create-op")
            .build()
        val repo = mockk<WorkspaceRepo>(relaxed = true)
        val api = mockk<WorkspaceApiService>()
        coEvery { api.createWorkspace(7, any<WorkspaceRequest>()) } throws ApiStatusException(403)
        val worker = WorkspaceSyncWorker(mockk<Context>(relaxed = true), params, api, repo)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 1) { repo.markRemoteWorkspaceCreationFailed(-17) }
    }
}
