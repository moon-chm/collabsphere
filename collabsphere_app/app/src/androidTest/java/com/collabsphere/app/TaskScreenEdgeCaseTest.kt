package com.collabsphere.app.view.task

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.collabsphere.app.ui.theme.CollabSphereTheme
import com.collabsphere.app.view.TaskUI.TaskScreen
import com.collabsphere.app.viewmodel.task.TaskUiModel
import com.collabsphere.app.viewmodel.task.TaskViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class TaskScreenEdgeCaseTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun taskScreenShowsEmptyStateWhenNoTasksExist() {
        val viewModel = mockk<TaskViewModel>(relaxed = true)
        val tasksFlow = MutableStateFlow<List<TaskUiModel>>(emptyList())

        coEvery { viewModel.tasks } returns tasksFlow
        coEvery { viewModel.workspaceMembers } returns MutableStateFlow(emptyList())
        coEvery { viewModel.availableLabels } returns MutableStateFlow(emptyList())
        coEvery { viewModel.labelFilter } returns MutableStateFlow(null)
        coEvery { viewModel.isSyncing } returns MutableStateFlow(false)

        compose.setContent {
            CollabSphereTheme(darkTheme = false) {
                TaskScreen(
                    viewModel = viewModel
                )
            }
        }

        // Verify empty state is shown
        compose.onNodeWithText("No tasks yet").assertIsDisplayed()
        compose.onNodeWithText("Tap the + button or create your first task to start tracking work.").assertIsDisplayed()
    }
}
