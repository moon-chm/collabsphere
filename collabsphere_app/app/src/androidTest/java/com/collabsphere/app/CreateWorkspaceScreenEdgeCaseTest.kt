package com.collabsphere.app.view.WorkspaceUI

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.collabsphere.app.model.workspace.WorkspaceRepo
import com.collabsphere.app.ui.theme.CollabSphereTheme
import com.collabsphere.app.viewmodel.workspace.WorkspaceViewModel
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class CreateWorkspaceScreenEdgeCaseTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyFormSubmissionDoesNotTriggerCreateWorkspace() {
        val repo = mockk<WorkspaceRepo>(relaxed = true)
        val viewModel = spyk(WorkspaceViewModel(repo, loggedInUserId = 1))

        compose.setContent {
            CollabSphereTheme(darkTheme = false) {
                CreateWorkspaceScreen(
                    viewModel = viewModel,
                    onBack = {}
                )
            }
        }

        // Initially fields are empty (verified by state flows in viewmodel)
        compose.onNodeWithText("Create workspace").performClick()

        // Verify that the repository is NOT called
        compose.runOnIdle {
            coVerify(exactly = 0) { repo.addWorkspaceToScreen(any(), any()) }
        }
    }
}
