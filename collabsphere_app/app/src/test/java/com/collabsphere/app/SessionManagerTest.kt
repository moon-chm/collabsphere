package com.collabsphere.app

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import androidx.work.Operation
import com.google.common.util.concurrent.ListenableFuture
import com.collabsphere.app.model.AppDatabase
import com.collabsphere.app.remote.login.LoginApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.IOException
import java.util.concurrent.TimeUnit

class SessionManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val appDatabase: AppDatabase = mockk(relaxed = true)
    private val userPreferences: UserPreferences = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private val loginApiService: LoginApiService = mockk(relaxed = true)

    private val sessionManager = SessionManager(context, appDatabase, userPreferences, workManager, loginApiService)

    @Before
    fun setUp() {
        mockkConstructor(Intent::class)
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `logout cancels queued sync work so it cannot run under the next user's session`() = runTest {
        sessionManager.logout()

        verify { workManager.cancelAllWork() }
    }

    @Test
    fun `logout unregisters the device while the session token is still valid`() = runTest {
        sessionManager.logout()

        coVerifyOrder {
            loginApiService.clearFcmToken()
            userPreferences.clearPreferences()
        }
    }

    @Test
    fun `logout still clears local data when the server cannot be reached`() = runTest {
        coEvery { loginApiService.clearFcmToken() } throws IOException("offline")

        sessionManager.logout()

        verify { appDatabase.clearAllTables() }
        coVerify { userPreferences.clearPreferences() }
    }

    @Test
    fun `logout waits for work cancellation before clearing account data`() = runTest {
        val events = mutableListOf<String>()
        val future = mockk<ListenableFuture<Operation.State.SUCCESS>>()
        val operation = mockk<Operation>()
        every { workManager.cancelAllWork() } returns operation
        every { operation.result } returns future
        every { future.get(any<Long>(), TimeUnit.MILLISECONDS) } answers {
            events.add("work-cancelled")
            Operation.SUCCESS
        }
        coEvery { loginApiService.clearFcmToken() } coAnswers {
            events += "fcm-unregistered"
            true
        }
        coEvery { appDatabase.clearAllTables() } coAnswers {
            events += "database-cleared"
            Unit
        }
        coEvery { userPreferences.clearPreferences() } coAnswers {
            events += "preferences-cleared"
            Unit
        }

        sessionManager.logout()

        assertEquals(
            listOf("fcm-unregistered", "work-cancelled", "database-cleared", "preferences-cleared"),
            events
        )
    }

    @Test
    fun `switching accounts clears prior local work and data before session replacement`() = runTest {
        coEvery { userPreferences.userIdFlow } returns flowOf(7)

        sessionManager.prepareForAuthenticatedUser(9)

        coVerify { loginApiService.clearFcmToken() }
        verify { workManager.cancelAllWork() }
        verify { appDatabase.clearAllTables() }
        coVerify { userPreferences.clearPreferences() }
    }

    @Test
    fun `logging in again as the same account preserves its local cache`() = runTest {
        coEvery { userPreferences.userIdFlow } returns flowOf(7)

        sessionManager.prepareForAuthenticatedUser(7)

        verify(exactly = 0) { workManager.cancelAllWork() }
        verify(exactly = 0) { appDatabase.clearAllTables() }
        coVerify(exactly = 0) { userPreferences.clearPreferences() }
    }
}
