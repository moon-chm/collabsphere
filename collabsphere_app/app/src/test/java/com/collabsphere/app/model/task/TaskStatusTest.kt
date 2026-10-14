package com.collabsphere.app.model.task

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskStatusTest {
    @Test
    fun `statuses are sent with the server spelling`() {
        assertEquals("TODO", TaskStatus.TO_DO.remote)
        assertEquals("IN_PROGRESS", TaskStatus.IN_PROGRESS.remote)
        assertEquals("DONE", TaskStatus.DONE.remote)
    }

    @Test
    fun `server values and legacy enum names both parse`() {
        assertEquals(TaskStatus.TO_DO, TaskStatus.fromRemote("TODO"))
        assertEquals(TaskStatus.TO_DO, TaskStatus.fromRemote("TO_DO"))
        assertEquals(TaskStatus.DONE, TaskStatus.fromRemote("DONE"))
        assertEquals(TaskStatus.TO_DO, TaskStatus.fromRemote(null))
        assertEquals(TaskStatus.TO_DO, TaskStatus.fromRemote("garbage"))
    }
}
