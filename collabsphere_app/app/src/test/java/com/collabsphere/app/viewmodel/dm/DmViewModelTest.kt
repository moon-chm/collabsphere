package com.collabsphere.app.viewmodel.dm

import android.content.Context
import com.collabsphere.app.NotificationHelper
import com.collabsphere.app.model.dm.DmRepo
import com.collabsphere.app.model.workspace.WorkspaceRepo
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
class DmViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: DmRepo

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
    fun `rapid repeated media sends dispatch only one request while first is pending`() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Unit>()
        coEvery { repo.sendMediaDm(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            requestStarted.complete(Unit)
            finishRequest.await()
        }
        val viewModel = DmViewModel(
            repo = repo,
            workspaceRepo = mockk<WorkspaceRepo>(relaxed = true),
            notificationHelper = mockk<NotificationHelper>(relaxed = true),
            context = mockk<Context>(relaxed = true)
        )

        viewModel.sendMediaMessage("https://test", 3, 7, 8, byteArrayOf(1), "image/png", "image.png")
        viewModel.sendMediaMessage("https://test", 3, 7, 8, byteArrayOf(1), "image/png", "image.png")

        assertTrue(viewModel.isUploadingMedia.value)
        runCurrent()
        requestStarted.await()
        coVerify(exactly = 1) { repo.sendMediaDm(any(), any(), any(), any(), any(), any(), any()) }

        finishRequest.complete(Unit)
        runCurrent()
        assertFalse(viewModel.isUploadingMedia.value)
        coVerify(exactly = 1) { repo.sendMediaDm(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `rapid repeated text sends dispatch only one request while first is pending`() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Unit>()
        coEvery { repo.sendRealtimeDm(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            requestStarted.complete(Unit)
            finishRequest.await()
        }
        val viewModel = DmViewModel(
            repo = repo,
            workspaceRepo = mockk<WorkspaceRepo>(relaxed = true),
            notificationHelper = mockk<NotificationHelper>(relaxed = true),
            context = mockk<Context>(relaxed = true)
        )

        viewModel.sendMessage(0, 3, 7, 8, "hello")
        viewModel.sendMessage(0, 3, 7, 8, "hello")

        assertTrue(viewModel.isSendingMessage.value)
        runCurrent()
        requestStarted.await()
        coVerify(exactly = 1) { repo.sendRealtimeDm(any(), any(), any(), any(), any(), any(), any()) }

        finishRequest.complete(Unit)
        runCurrent()
        assertFalse(viewModel.isSendingMessage.value)
        coVerify(exactly = 1) { repo.sendRealtimeDm(any(), any(), any(), any(), any(), any(), any()) }
    }
}
