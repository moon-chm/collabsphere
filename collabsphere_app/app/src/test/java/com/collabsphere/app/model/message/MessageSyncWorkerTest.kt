package com.collabsphere.app.model.message

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.dto.message.MessageRequest
import com.collabsphere.app.dto.message.MessageResponse
import com.collabsphere.app.model.OutboxSyncStatus
import com.collabsphere.app.remote.ApiStatusException
import com.collabsphere.app.remote.message.MessageApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class MessageSyncWorkerTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.i(any(), any<String>(), any()) } returns 0
        every { Log.d(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `aborts when work item userId does not match authenticated userId in DataStore`() = runBlocking {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.inputData } returns Data.Builder()
            .putString("ACTION_TYPE", "CREATE")
            .putInt("MESSAGE_ID", -1)
            .putInt("USER_ID", 100)
            .putInt("WORKSPACE_ID", 1)
            .putInt("CHANNEL_ID", 2)
            .build()

        val dataStore = mockk<DataStore<Preferences>>()
        every { dataStore.data } returns flowOf(
            mutablePreferencesOf(UserPreferences.USER_ID to 200) // Different user logged in
        )

        val api = mockk<MessageApiService>(relaxed = true)
        val dao = mockk<MessageDao>(relaxed = true)
        val worker = MessageSyncWorker(mockk<Context>(relaxed = true), params, api, dao, dataStore)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { api.createMessage(any()) }
    }

    @Test
    fun `preserves clientMutationId from Room entity and replaces tempId on successful create`() = runBlocking {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.inputData } returns Data.Builder()
            .putString("ACTION_TYPE", "CREATE")
            .putInt("MESSAGE_ID", -15)
            .putInt("USER_ID", 42)
            .putInt("WORKSPACE_ID", 1)
            .putInt("CHANNEL_ID", 2)
            .putString("CONTENT", "Hello offline world")
            .build()

        val dataStore = mockk<DataStore<Preferences>>(relaxed = true)
        every { dataStore.data } returns flowOf(
            mutablePreferencesOf(UserPreferences.USER_ID to 42)
        )

        val dao = mockk<MessageDao>(relaxed = true)
        val existingRoomMessage = MessageEntity(
            id = -15,
            userId = 42,
            workspaceId = 1,
            channelId = 2,
            userName = "TestUser",
            content = "Hello offline world",
            clientMutationId = "deterministic-uuid-12345"
        )
        coEvery { dao.getMessageById(-15) } returns existingRoomMessage

        val api = mockk<MessageApiService>()
        val requestSlot = slot<MessageRequest>()
        coEvery { api.createMessage(capture(requestSlot)) } returns MessageResponse(
            id = 999,
            userId = 42,
            workspaceId = 1,
            channelId = 2,
            userName = "TestUser",
            content = "Hello offline world",
            status = "Delivered"
        )

        val worker = MessageSyncWorker(mockk<Context>(relaxed = true), params, api, dao, dataStore)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals("deterministic-uuid-12345", requestSlot.captured.idempotencyKey)
        coVerify(exactly = 1) { dao.replaceTempId(-15, 999) }
    }

    @Test
    fun `permanent rejection marks message as FAILED_PERMANENT in Room`() = runBlocking {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.inputData } returns Data.Builder()
            .putString("ACTION_TYPE", "CREATE")
            .putInt("MESSAGE_ID", -25)
            .putInt("USER_ID", 42)
            .putInt("WORKSPACE_ID", 1)
            .putInt("CHANNEL_ID", 2)
            .build()

        val dataStore = mockk<DataStore<Preferences>>(relaxed = true)
        every { dataStore.data } returns flowOf(
            mutablePreferencesOf(UserPreferences.USER_ID to 42)
        )

        val dao = mockk<MessageDao>(relaxed = true)
        coEvery { dao.getMessageById(-25) } returns null

        val api = mockk<MessageApiService>()
        coEvery { api.createMessage(any()) } throws ApiStatusException(400) // 400 Bad Request (permanent failure)

        val worker = MessageSyncWorker(mockk<Context>(relaxed = true), params, api, dao, dataStore)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 1) { dao.updateSyncStatus(-25, OutboxSyncStatus.FAILED_PERMANENT) }
    }

    @Test
    fun `transient network timeout retries without marking permanent failure`() = runBlocking {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.inputData } returns Data.Builder()
            .putString("ACTION_TYPE", "CREATE")
            .putInt("MESSAGE_ID", -35)
            .putInt("USER_ID", 42)
            .putInt("WORKSPACE_ID", 1)
            .putInt("CHANNEL_ID", 2)
            .build()

        val dataStore = mockk<DataStore<Preferences>>(relaxed = true)
        every { dataStore.data } returns flowOf(
            mutablePreferencesOf(UserPreferences.USER_ID to 42)
        )

        val dao = mockk<MessageDao>(relaxed = true)
        coEvery { dao.getMessageById(-35) } returns null

        val api = mockk<MessageApiService>()
        coEvery { api.createMessage(any()) } throws IOException("Connection timed out")

        val worker = MessageSyncWorker(mockk<Context>(relaxed = true), params, api, dao, dataStore)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        coVerify(exactly = 0) { dao.updateSyncStatus(any(), OutboxSyncStatus.FAILED_PERMANENT) }
    }
}
