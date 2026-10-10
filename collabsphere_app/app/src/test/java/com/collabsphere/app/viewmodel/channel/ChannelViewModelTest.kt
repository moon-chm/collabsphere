package com.collabsphere.app.viewmodel.channel

import com.collabsphere.app.model.channels.ChannelRepo
import com.collabsphere.app.model.channels.ChannelEntity
import io.mockk.coEvery
// removed
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class ChannelViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: ChannelRepo

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        every { repo.getallchannelbyuser(3) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rapid repeated channel creation dispatches one request while first is pending`() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Result<Long>>()
        coEvery { repo.addchanneltoscreen(any(), any(), any(), any(), any()) } coAnswers {
            requestStarted.complete(Unit)
            finishRequest.await()
        }
        val viewModel = ChannelViewModel(repo, loggeduserID = 7, loggedWorkspaceId = 3)
        viewModel.onChannelNamechange("Roadmap")

        viewModel.onCreateChannel()
        viewModel.onCreateChannel()

        assertTrue(viewModel.isCreatingChannel.value)
        runCurrent()
        requestStarted.await()
        coVerify(exactly = 1) { repo.addchanneltoscreen(any(), any(), any(), any(), any()) }

        finishRequest.complete(Result.success(15L))
        runCurrent()
        assertFalse(viewModel.isCreatingChannel.value)
        coVerify(exactly = 1) { repo.addchanneltoscreen(any(), any(), any(), any(), any()) }
    }
}
