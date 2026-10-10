package com.collabsphere.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.collabsphere.app.model.workspace.WorkspaceEntity
import com.collabsphere.app.ui.theme.CollabSphereTheme
import com.collabsphere.app.view.DashboardScreen
import com.collabsphere.app.viewmodel.DashboardViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class DashboardScreenEdgeCaseTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dashboardShowsEmptyStateWhenNoWorkspacesExist() {
        // Setup mock view model with empty workspaces
        val viewModel = mockk<DashboardViewModel>(relaxed = true)
        val workspacesFlow = MutableStateFlow<List<WorkspaceEntity>?>(emptyList())
        val isRefreshingFlow = MutableStateFlow(false)
        val userIdFlow = MutableStateFlow(1)

        coEvery { viewModel.workspaces } returns workspacesFlow
        coEvery { viewModel.isRefreshing } returns isRefreshingFlow
        coEvery { viewModel.userIdState } returns userIdFlow

        compose.setContent {
            CollabSphereTheme(darkTheme = false) {
                DashboardScreen(
                    viewModel = viewModel,
                    currentUserName = "TestUser",
                    avatarUrl = "",
                    onNavigateToWorkspace = {},
                    onWorkspaceClick = {},
                    onDeleteWorkspaceClick = {}
                )
            }
        }

        // Verify empty state is shown
        compose.onNodeWithText("No workspaces yet").assertIsDisplayed()
        compose.onNodeWithText("Tap the + button below or create your first workspace to start collaborating.").assertIsDisplayed()
    }
}
