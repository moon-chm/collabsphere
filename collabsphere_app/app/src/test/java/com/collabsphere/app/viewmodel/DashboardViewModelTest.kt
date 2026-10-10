package com.collabsphere.app.viewmodel

import com.collabsphere.app.SessionManager
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.model.workspace.WorkspaceEntity
import com.collabsphere.app.model.workspace.WorkspaceRepo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var repository: WorkspaceRepo
    private lateinit var userPreferences: UserPreferences
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: DashboardViewModel

    private val mockUserIdFlow = MutableStateFlow(-1)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        repository = mockk(relaxed = true)
        userPreferences = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)

        every { userPreferences.userIdFlow } returns mockUserIdFlow

        viewModel = DashboardViewModel(
            repository = repository,
            initialUserId = -1,
            userPreferences = userPreferences,
            sessionManager = sessionManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when userId is valid, workspaces are synced and delta loop starts`() = runTest {
        // Arrange & Act - emit a valid user ID (e.g. 1)
        mockUserIdFlow.value = 1
        
        // Let the coroutines execute
        testScheduler.advanceUntilIdle()

        // Assert that the repository was told to sync workspaces and start the delta loop
        coVerify(exactly = 1) { repository.syncWorkspaces(1) }
        coVerify(exactly = 1) { repository.startDeltaSyncLoop(1) }
    }

    @Test
    fun `when logout is called, sessionManager is cleared`() = runTest {
        // Act
        viewModel.logout()
        
        // Let the coroutines execute
        testScheduler.advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { sessionManager.logout() }
    }
}
