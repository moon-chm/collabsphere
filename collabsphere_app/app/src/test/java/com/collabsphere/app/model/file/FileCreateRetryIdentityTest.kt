package com.collabsphere.app.model.file

import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.collabsphere.app.remote.file.FileApiService
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
import java.io.File
import java.io.IOException

class FileCreateRetryIdentityTest {
    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() = unmockkStatic(Log::class)

    @Test
    fun `ambiguous file upload reuses the server key in queued work`() = runBlocking {
        val physicalFile = File.createTempFile("collabsphere", ".txt").apply { writeText("hello") }
        try {
            val apiKey = slot<String?>()
            val queuedWork = slot<OneTimeWorkRequest>()
            val api = mockk<FileApiService>()
            val dao = mockk<FileDao>(relaxed = true)
            val workManager = mockk<WorkManager>(relaxed = true)
            coEvery {
                api.uploadFile(any(), any(), any(), any(), any(), captureNullable(apiKey))
            } throws IOException("response timed out")
            coEvery { dao.insertFile(any()) } returns -22L
            val repo = FileRepo(dao, api, workManager, mockk(relaxed = true))

            repo.uploadfilestoscreen(
                FileEntity(
                    id = 0,
                    userId = 7,
                    workspaceId = 3,
                    userName = "Alex",
                    url = "",
                    mimeType = "text/plain",
                    localpath = physicalFile.absolutePath,
                    fileName = physicalFile.name,
                    sizebytes = physicalFile.length()
                )
            )

            assertNotNull(apiKey.captured)
            verify(exactly = 1) {
                workManager.enqueueUniqueWork(
                    match { it.startsWith("FILE_SYNC_") },
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    capture(queuedWork)
                )
            }
            assertEquals(apiKey.captured, queuedWork.captured.workSpec.input.getString("IDEMPOTENCY_KEY"))
        } finally {
            physicalFile.delete()
        }
    }
}
