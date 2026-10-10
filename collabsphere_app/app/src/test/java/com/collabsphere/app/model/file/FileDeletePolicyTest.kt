package com.collabsphere.app.model.file

import android.util.Log
import androidx.work.WorkManager
import com.collabsphere.app.remote.file.FileApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class FileDeletePolicyTest {
    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.i(any(), any<String>(), any()) } returns 0
        every { Log.d(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `permanent delete refusal keeps the local file and does not queue retry`() = runBlocking {
        val fileDao = mockk<FileDao>(relaxed = true)
        val api = mockk<FileApiService>()
        val workManager = mockk<WorkManager>(relaxed = true)
        coEvery { api.deleteFile(19L) } returns HttpStatusCode.Forbidden
        val repo = FileRepo(fileDao, api, workManager, mockk(relaxed = true))

        repo.deletefiles(19L)

        coVerify(exactly = 0) { fileDao.deleteFileById(19L) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<androidx.work.OneTimeWorkRequest>()) }
    }
}
