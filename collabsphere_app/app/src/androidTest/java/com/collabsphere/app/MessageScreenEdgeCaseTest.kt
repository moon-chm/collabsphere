package com.collabsphere.app.view.message

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.collabsphere.app.model.message.MessageEntity
import com.collabsphere.app.ui.theme.CollabSphereTheme
import com.collabsphere.app.view.MessageUI.MessageScreen
import com.collabsphere.app.viewmodel.message.MessageViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class MessageScreenEdgeCaseTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun messageScreenShowsEmptyStateWhenNoMessagesExist() {
        val viewModel = mockk<MessageViewModel>(relaxed = true)
        val messagesFlow = MutableStateFlow<List<MessageEntity>?>(emptyList())

        coEvery { viewModel.messages } returns messagesFlow
        coEvery { viewModel.messageContent } returns MutableStateFlow("")
        coEvery { viewModel.typingUsers } returns MutableStateFlow(emptyList())
        coEvery { viewModel.replyingTo } returns MutableStateFlow(null)
        coEvery { viewModel.pinned } returns MutableStateFlow(emptyList())
        coEvery { viewModel.readStates } returns MutableStateFlow(emptyMap())
        coEvery { viewModel.reactions } returns MutableStateFlow(emptyMap())
        coEvery { viewModel.isLoadingOlder } returns MutableStateFlow(false)
        coEvery { viewModel.hasMoreOlder } returns MutableStateFlow(false)
        coEvery { viewModel.currentUserId } returns 1

        val channelName = "general"

        compose.setContent {
            CollabSphereTheme(darkTheme = false) {
                MessageScreen(
                    viewModel = viewModel,
                    channelName = channelName,
                    onBack = {}
                )
            }
        }

        // Verify empty state is shown
        compose.onNodeWithText("Welcome to #general").assertIsDisplayed()
        compose.onNodeWithText("This is the start of the #general channel. Send a message to start collaborating!").assertIsDisplayed()
    }
}
