package com.collabsphere.app.view.channel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.collabsphere.app.model.channels.ChannelEntity
import com.collabsphere.app.ui.theme.CollabSphereTheme
import com.collabsphere.app.viewmodel.channel.ChannelViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class ChannelScreenEdgeCaseTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun channelScreenShowsEmptyStateWhenNoChannelsExist() {
        val viewModel = mockk<ChannelViewModel>(relaxed = true)
        val channelsFlow = MutableStateFlow<List<ChannelEntity>?>(emptyList())
        
        coEvery { viewModel.userChannels } returns channelsFlow
        coEvery { viewModel.channelStatus } returns MutableStateFlow(null)
        coEvery { viewModel.isCreatingChannel } returns MutableStateFlow(false)
        coEvery { viewModel.deletingChannelKeys } returns MutableStateFlow(emptySet())
        coEvery { viewModel.channelName } returns MutableStateFlow("")
        coEvery { viewModel.description } returns MutableStateFlow("")

        compose.setContent {
            CollabSphereTheme(darkTheme = false) {
                ChannelScreen(
                    viewModel = viewModel,
                    onChannelClick = {}
                )
            }
        }

        // Verify empty state is shown
        compose.onNodeWithText("No channels yet").assertIsDisplayed()
        compose.onNodeWithText("Tap 'Add channel' below to organize conversations around topics and projects.").assertIsDisplayed()
    }
}
