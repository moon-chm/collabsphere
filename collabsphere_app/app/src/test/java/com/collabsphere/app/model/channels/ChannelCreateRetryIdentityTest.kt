package com.collabsphere.app.model.channels

import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.collabsphere.app.dto.channel.ChannelRequest
import com.collabsphere.app.remote.channel.ChannelApiService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ChannelCreateRetryIdentityTest {
    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() = unmockkStatic(Log::class)

    @Test
    fun `ambiguous channel create reuses the server request key in queued work`() = runBlocking {
        val apiRequest = slot<ChannelRequest>()
        val queuedWork = slot<OneTimeWorkRequest>()
        val api = mockk<ChannelApiService>()
        val dao = mockk<ChannelDao>()
        val workManager = mockk<WorkManager>(relaxed = true)
        coEvery { api.createChannel(capture(apiRequest)) } throws IOException("response timed out")
        coEvery { dao.createChannels(any()) } returns -18L

        val repo = ChannelRepo(dao, api, workManager, mockk(relaxed = true))
        val result = repo.addchanneltoscreen(
            workspaceId = 3,
            channelName = "Roadmap",
            userId = 7,
            description = "Planning",
            fallbackEntity = ChannelEntity(-18, 7, "Roadmap", 3, "Planning")
        )

        assertTrue(result.isSuccess)
        val requestKey = apiRequest.captured.idempotencyKey
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                match { it.startsWith("CHANNEL_SYNC_") },
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                capture(queuedWork)
            )
        }
        assertEquals(requestKey, queuedWork.captured.workSpec.input.getString("IDEMPOTENCY_KEY"))
    }
}

