package com.collabsphere.app

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

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
}
