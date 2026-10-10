package com.collabsphere.app.viewmodel.workspace

import com.collabsphere.app.model.workspace.WorkspaceRepo
import com.collabsphere.app.dto.workspace.InvitationResponse
import com.collabsphere.app.dto.workspace.MemberResponse
import io.mockk.coEvery
// removed
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: WorkspaceRepo

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rapid repeated create calls dispatch only one request while first is pending`() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Result<Unit>>()
        coEvery { repo.addWorkspaceToScreen(any(), any()) } coAnswers {
            requestStarted.complete(Unit)
            finishRequest.await()
        }
        val viewModel = WorkspaceViewModel(repo, loggedInUserId = 7)
        viewModel.onNameChange("Design")
        viewModel.onOwnerChange("Alex")
        viewModel.onPasswordChange("secret")

        viewModel.onCreateWorkspace()
        viewModel.onCreateWorkspace()

        assertTrue(viewModel.isCreatingWorkspace.value)
        runCurrent()
        requestStarted.await()
        coVerify(exactly = 1) { repo.addWorkspaceToScreen(any(), any()) }

        finishRequest.complete(Result.success(Unit))
        runCurrent()
        assertFalse(viewModel.isCreatingWorkspace.value)
        coVerify(exactly = 1) { repo.addWorkspaceToScreen(any(), any()) }
    }

    @Test
    fun `rapid repeated delete calls dispatch only one request while first is pending`() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Int>()
        coEvery { repo.deleteWorkspaceFromScreen(42, "secret") } coAnswers {
            requestStarted.complete(Unit)
            finishRequest.await()
        }
        val viewModel = WorkspaceViewModel(repo, loggedInUserId = 7)
        viewModel.onNameChange("Design")
        viewModel.onPasswordChange("secret")

        viewModel.onDeleteWorkspace(42, "Design")
        viewModel.onDeleteWorkspace(42, "Design")

        assertTrue(viewModel.isDeletingWorkspace.value)
        runCurrent()
        requestStarted.await()
        coVerify(exactly = 1) { repo.deleteWorkspaceFromScreen(42, "secret") }

        finishRequest.complete(1)
        runCurrent()
        assertFalse(viewModel.isDeletingWorkspace.value)
        coVerify(exactly = 1) { repo.deleteWorkspaceFromScreen(42, "secret") }
    }

    @Test
    fun `rapid repeated invitation calls dispatch only one request while first is pending`() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Result<InvitationResponse>>()
        coEvery { repo.sendInvitation(42, "member@example.com") } coAnswers {
            requestStarted.complete(Unit)
            finishRequest.await()
        }
        val viewModel = WorkspaceViewModel(repo, loggedInUserId = 7)

        viewModel.sendInvitation(42, " Member@Example.com ")
        viewModel.sendInvitation(42, "member@example.com")

        assertTrue(viewModel.isSendingInvitation.value)
        runCurrent()
        requestStarted.await()
        coVerify(exactly = 1) { repo.sendInvitation(42, "member@example.com") }

        finishRequest.complete(Result.failure(IllegalStateException("offline")))
        runCurrent()
        assertFalse(viewModel.isSendingInvitation.value)
        coVerify(exactly = 1) { repo.sendInvitation(42, "member@example.com") }
    }

    @Test
    fun `accept and decline cannot run concurrently for the same invitation`() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        val finishAccept = CompletableDeferred<Result<MemberResponse>>()
        coEvery { repo.acceptInvitation(55) } coAnswers {
            requestStarted.complete(Unit)
            finishAccept.await()
        }
        val viewModel = WorkspaceViewModel(repo, loggedInUserId = 7)

        viewModel.acceptInvitation(55)
        viewModel.declineInvitation(55)

        assertTrue(55 in viewModel.pendingInvitationIds.value)
        runCurrent()
        requestStarted.await()
        coVerify(exactly = 1) { repo.acceptInvitation(55) }
        coVerify(exactly = 0) { repo.declineInvitation(55) }

        finishAccept.complete(Result.failure(IllegalStateException("offline")))
        runCurrent()
        assertFalse(55 in viewModel.pendingInvitationIds.value)
        coVerify(exactly = 1) { repo.acceptInvitation(55) }
        coVerify(exactly = 0) { repo.declineInvitation(55) }
    }

    @Test
    fun `repeated join-by-code calls dispatch only one request while first is pending`() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Result<MemberResponse>>()
        coEvery { repo.joinWorkspaceByCode("ABC123", 7) } coAnswers {
            requestStarted.complete(Unit)
            finishRequest.await()
        }
        val viewModel = WorkspaceViewModel(repo, loggedInUserId = 7)

        viewModel.joinByCode("abc123")
        viewModel.joinByCode("ABC123")

        assertTrue(viewModel.isJoiningByCode.value)
        runCurrent()
        requestStarted.await()
        coVerify(exactly = 1) { repo.joinWorkspaceByCode("ABC123", 7) }

        finishRequest.complete(Result.failure(IllegalStateException("offline")))
        runCurrent()
        assertFalse(viewModel.isJoiningByCode.value)
        coVerify(exactly = 1) { repo.joinWorkspaceByCode("ABC123", 7) }
    }
}
