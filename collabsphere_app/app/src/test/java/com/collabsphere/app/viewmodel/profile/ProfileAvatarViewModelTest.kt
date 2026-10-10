package com.collabsphere.app.viewmodel.profile

import com.collabsphere.app.SessionManager
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.model.UserRepo
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileAvatarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: UserRepo

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
    fun `repeated avatar selection dispatches once and blocks removal until upload completes`() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        val finishUpload = CompletableDeferred<Result<String>>()
        val file = File.createTempFile("avatar-edge", ".png")
        coEvery { repo.uploadAvatar(7, file) } coAnswers {
            requestStarted.complete(Unit)
            finishUpload.await()
        }
        val viewModel = ProfileViewModel(
            loggedInUserId = 7,
            userEmail = "alex@example.com",
            repo = repo,
            userPreferences = mockk<UserPreferences>(relaxed = true),
            sessionManager = mockk<SessionManager>(relaxed = true)
        )
        try {
            viewModel.onAvatarPicked(file)
            viewModel.onAvatarPicked(file)
            viewModel.onRemoveAvatar()

            assertTrue(viewModel.isUploadingAvatar.value)
            runCurrent()
            requestStarted.await()
            coVerify(exactly = 1) { repo.uploadAvatar(7, file) }
            coVerify(exactly = 0) { repo.removeAvatar(7) }

            finishUpload.complete(Result.success("avatar-url"))
            runCurrent()
            assertFalse(viewModel.isUploadingAvatar.value)
            coVerify(exactly = 1) { repo.uploadAvatar(7, file) }
            coVerify(exactly = 0) { repo.removeAvatar(7) }
        } finally {
            file.delete()
        }
    }
}
