package com.collabsphere.app.model.task

import android.util.Log
import androidx.work.WorkManager
import com.collabsphere.app.model.workspace.WorkspaceDao
import com.collabsphere.app.remote.task.TaskApiService
import com.collabsphere.app.remote.workspace.WorkspaceApiService
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class TaskMutationPolicyTest {
    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `permanent task create refusal does not create a local placeholder or retry`() = runBlocking {
        val taskDao = mockk<TaskDao>(relaxed = true)
        val api = mockk<TaskApiService>()
        val workManager = mockk<WorkManager>(relaxed = true)
        coEvery { api.createTask(7, any()) } throws com.collabsphere.app.remote.ApiStatusException(400)
        val repo = TaskRepo(
            taskDao,
            mockk<WorkspaceDao>(relaxed = true),
            api,
            mockk<WorkspaceApiService>(relaxed = true),
            workManager,
            mockk(relaxed = true)
        )

        val result = repo.addTask(
            TaskEntity(0, 7, null, 3, "Release", "", status = TaskStatus.TO_DO)
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { taskDao.insertTask(any()) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<androidx.work.OneTimeWorkRequest>()) }
    }

    @Test
    fun `permanent task delete refusal keeps the local task and is not queued`() = runBlocking {
        val taskDao = mockk<TaskDao>(relaxed = true)
        val api = mockk<TaskApiService>()
        val workManager = mockk<WorkManager>(relaxed = true)
        coEvery { api.deleteTask(12) } returns HttpStatusCode.Forbidden
        val repo = TaskRepo(
            taskDao,
            mockk<WorkspaceDao>(relaxed = true),
            api,
            mockk<WorkspaceApiService>(relaxed = true),
            workManager,
            mockk(relaxed = true)
        )

        val result = repo.deleteTask(12)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { taskDao.deleteTask(12) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<androidx.work.OneTimeWorkRequest>()) }
    }
}
