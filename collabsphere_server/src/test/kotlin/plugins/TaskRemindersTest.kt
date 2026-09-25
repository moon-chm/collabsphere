package plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskRemindersTest {

    private val day = 24 * 60 * 60 * 1000L
    private val due = 1_760_000_000_000L

    @Test
    fun `reminder is not due more than a day before the due date`() {
        assertFalse(isReminderDue(due, due - day - 1))
    }

    @Test
    fun `reminder is due within the day before the due date`() {
        assertTrue(isReminderDue(due, due - day))
        assertTrue(isReminderDue(due, due - 1))
    }

    @Test
    fun `reminder is due during the due day itself`() {
        assertTrue(isReminderDue(due, due))
        assertTrue(isReminderDue(due, due + day - 1))
    }

    @Test
    fun `reminder is not sent once the due day has passed`() {
        assertFalse(isReminderDue(due, due + day))
    }

    @Test
    fun `reminder body says tomorrow before the due day and today on it`() {
        assertEquals("\"Ship\" is due tomorrow", reminderBody("Ship", due, due - 1))
        assertEquals("\"Ship\" is due today", reminderBody("Ship", due, due))
    }

    @Test
    fun `priority normalization accepts known values only`() {
        assertEquals("HIGH", dto.TaskPriorities.normalize(" high "))
        assertEquals(null, dto.TaskPriorities.normalize("urgent"))
        assertEquals(null, dto.TaskPriorities.normalize(null))
    }
}
