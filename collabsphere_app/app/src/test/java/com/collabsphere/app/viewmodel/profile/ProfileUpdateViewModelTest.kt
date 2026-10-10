package com.collabsphere.app.viewmodel.profile

import com.collabsphere.app.SessionManager
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.model.UserRepo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileUpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: UserRepo
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        viewModel = ProfileViewModel(
            loggedInUserId = 7,
            userEmail = "alex@example.com",
            repo = repo,
            userPreferences = mockk<UserPreferences>(relaxed = true),
            sessionManager = mockk<SessionManager>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `oversized profile fields are rejected before repository update`() = runTest(dispatcher) {
        viewModel.onUserNameChanged("u".repeat(51))
        viewModel.onUpdateProfile()

        assertEquals("Username must be between 3 and 50 characters", viewModel.profileStatus.value)
        coVerify(exactly = 0) { repo.updateProfile(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `password fields retain intentional surrounding spaces`() = runTest(dispatcher) {
        coEvery { repo.updateProfile(any(), any(), any(), any(), any(), any(), any()) } returns true
        viewModel.onUserNameChanged("valid-user")
        viewModel.onCurrentPasswordChanged(" current ")
        viewModel.onNewPasswordChanged(" new-password ")

        viewModel.onUpdateProfile()
        runCurrent()

        coVerify(exactly = 1) {
            repo.updateProfile(
                userId = 7,
                userEmail = "alex@example.com",
                newName = "valid-user",
                bio = null,
                statusMessage = null,
                currentPassword = " current ",
                newPassword = " new-password "
            )
        }
    }
}
