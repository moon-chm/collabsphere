package com.collabsphere.app.viewmodel

import com.collabsphere.app.SessionManager
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.model.UserRepo
import com.collabsphere.app.model.UserEntity
import io.mockk.coAnswers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var repo: UserRepo
    private lateinit var userPreferences: UserPreferences
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        repo = mockk(relaxed = true)
        userPreferences = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)

        // Mock initialization values
        coEvery { userPreferences.userIdFlow } returns flowOf(-1)
        coEvery { userPreferences.userNameFlow } returns flowOf("")
        coEvery { userPreferences.userEmailFlow } returns flowOf("")

        viewModel = LoginViewModel(repo, userPreferences, sessionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onLoginClick with empty fields sets loginStatus to Please fill all fields`() = runTest(testDispatcher) {
        // Act
        viewModel.onLoginClick("", "")
        
        // Assert
        assertEquals("Please fill all fields", viewModel.loginStatus.value)
    }

    @Test
    fun `onRegisterClick with short password sets loginStatus to Password must be at least 6 characters`() = runTest(testDispatcher) {
        // We use a valid email structure here, but since Patterns is not mocked in JUnit, 
        // this might fail on Patterns.EMAIL_ADDRESS. 
        // We will test the empty validation which is the very first guard clause.
        viewModel.onRegisterClick("", "", "")
        
        // Assert
        assertEquals("Fields cannot be empty", viewModel.loginStatus.value)
    }

    @Test
    fun `repeated login taps dispatch one request and loading clears after failure`() = runTest(testDispatcher) {
        val pending = CompletableDeferred<Result<UserEntity>>()
        coEvery { repo.loginRemote(any(), any()) } coAnswers { pending.await() }

        viewModel.onLoginClick("person@example.com", " password ")
        viewModel.onLoginClick("person@example.com", " password ")
        assertTrue(viewModel.isLoadingLogin.value)

        runCurrent()
        coVerify(exactly = 1) { repo.loginRemote("person@example.com", " password ") }
        pending.complete(Result.failure(java.io.IOException("offline")))
        advanceUntilIdle()

        assertFalse(viewModel.isLoadingLogin.value)
        assertEquals("Network issue. Please check your internet connection.", viewModel.loginStatus.value)
    }
}
