package com.collabsphere.app.model.task

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.collabsphere.app.remote.task.TaskApiService
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSyncWorkerTest {
    @Test
    fun `dependent delete resolves a temporary task ID of negative one`() = runBlocking {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.getInputData() } returns Data.Builder()
            .putString("ACTION_TYPE", "DELETE")
            .putInt("TASK_ID", -1)
            .build()
        val dataStore = mockk<DataStore<Preferences>>()
        every { dataStore.data } returns flowOf(
            mutablePreferencesOf(intPreferencesKey("temp_task_-1") to 42)
        )
        val api = mockk<TaskApiService>()
        coEvery { api.deleteTask(42) } returns HttpStatusCode.OK
        val worker = TaskSyncWorker(mockk<Context>(relaxed = true), params, api, mockk(relaxed = true), dataStore)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { api.deleteTask(42) }
    }

    @Test
    fun `dependent task delete retries until its create mapping becomes available`() = runBlocking {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.getInputData() } returns Data.Builder()
            .putString("ACTION_TYPE", "DELETE")
            .putInt("TASK_ID", -17)
            .build()
        val dataStore = mockk<DataStore<Preferences>>()
        every { dataStore.data } returns flowOf(emptyPreferences())
        val api = mockk<TaskApiService>(relaxed = true)
        val worker = TaskSyncWorker(mockk<Context>(relaxed = true), params, api, mockk(relaxed = true), dataStore)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        coVerify(exactly = 0) { api.deleteTask(any()) }
    }
}
