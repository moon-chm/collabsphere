package com.collabsphere.app.viewmodel.file

import com.collabsphere.app.model.file.FileRepo
import io.mockk.coEvery
// removed
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FileRepo

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        every { repo.getfiles(3) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rapid repeated upload calls dispatch only one request while first is pending`() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Long>()
        coEvery { repo.uploadfilestoscreen(any()) } coAnswers {
            requestStarted.complete(Unit)
            finishRequest.await()
        }
        val file = File.createTempFile("upload-edge", ".txt").apply { writeText("content") }
        try {
            val viewModel = FileViewModel(repo, loggedUserId = 7, loggedWorkspaceId = 3, loggedUserName = "Alex")

            viewModel.uploadPhysicalFile(file, "text/plain")
            viewModel.uploadPhysicalFile(file, "text/plain")

            assertTrue(viewModel.uploadingStatus.value)
            runCurrent()
            requestStarted.await()
            coVerify(exactly = 1) { repo.uploadfilestoscreen(any()) }

            finishRequest.complete(12L)
            runCurrent()
            assertFalse(viewModel.uploadingStatus.value)
            coVerify(exactly = 1) { repo.uploadfilestoscreen(any()) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `rapid repeated file delete calls dispatch only one request while first is pending`() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Unit>()
        coEvery { repo.deletefiles(12L) } coAnswers {
            requestStarted.complete(Unit)
            finishRequest.await()
        }
        val viewModel = FileViewModel(repo, loggedUserId = 7, loggedWorkspaceId = 3, loggedUserName = "Alex")

        viewModel.deleteFile(12L)
        viewModel.deleteFile(12L)

        assertTrue(12L in viewModel.deletingFileIds.value)
        runCurrent()
        requestStarted.await()
        coVerify(exactly = 1) { repo.deletefiles(12L) }

        finishRequest.complete(Unit)
        runCurrent()
        assertFalse(12L in viewModel.deletingFileIds.value)
        coVerify(exactly = 1) { repo.deletefiles(12L) }
    }
}
