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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileDeleteViewModelTest {
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
    fun `repeated account delete confirmations dispatch once and clear loading after failure`() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Result<Unit>>()
        coEvery { repo.deleteAccountRemote("secret") } coAnswers {
            requestStarted.complete(Unit)
            finishRequest.await()
        }
        val viewModel = ProfileViewModel(
            loggedInUserId = 7,
            userEmail = "alex@example.com",
            repo = repo,
            userPreferences = mockk<UserPreferences>(relaxed = true),
            sessionManager = mockk<SessionManager>(relaxed = true)
        )
        viewModel.onDeleteAccountPasswordChanged("secret")

        viewModel.onConfirmDeleteAccount {}
        viewModel.onConfirmDeleteAccount {}

        assertTrue(viewModel.isDeletingAccount.value)
        runCurrent()
        requestStarted.await()
        coVerify(exactly = 1) { repo.deleteAccountRemote("secret") }

        finishRequest.complete(Result.failure(IllegalStateException("offline")))
        runCurrent()
        assertFalse(viewModel.isDeletingAccount.value)
        coVerify(exactly = 1) { repo.deleteAccountRemote("secret") }
    }
}
