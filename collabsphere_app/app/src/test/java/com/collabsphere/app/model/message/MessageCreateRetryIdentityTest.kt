package com.collabsphere.app.model.message

import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.collabsphere.app.dto.message.MessageRequest
import com.collabsphere.app.remote.message.MessageApiService
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

class MessageCreateRetryIdentityTest {
    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() = unmockkStatic(Log::class)

    @Test
    fun `ambiguous message create reuses the server request key in queued work`() = runBlocking {
        val apiRequest = slot<MessageRequest>()
        val queuedWork = slot<OneTimeWorkRequest>()
        val api = mockk<MessageApiService>()
        val dao = mockk<MessageDao>()
        val workManager = mockk<WorkManager>(relaxed = true)
        coEvery { api.createMessage(capture(apiRequest)) } throws IOException("response timed out")
        coEvery { dao.sendMessage(any()) } returns -19L

        val repo = MessageRepo(
            messageDao = dao,
            apiService = api,
            workManager = workManager,
            dataStore = mockk(relaxed = true),
            dmApiService = mockk(relaxed = true),
            mediaApiService = mockk(relaxed = true)
        )
        val localMessage = MessageEntity(
            id = 0,
            userId = 7,
            workspaceId = 3,
            channelId = 5,
            userName = "Alex",
            content = "Hello",
            status = MessageStatus.Delivered
        )

        repo.sendMessageToUser(localMessage)

        val requestKey = apiRequest.captured.idempotencyKey
        assertNotNull(requestKey)
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                match { it.startsWith("MESSAGE_SYNC_") },
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                capture(queuedWork)
            )
        }
        assertEquals(requestKey, queuedWork.captured.workSpec.input.getString("IDEMPOTENCY_KEY"))
    }
}
