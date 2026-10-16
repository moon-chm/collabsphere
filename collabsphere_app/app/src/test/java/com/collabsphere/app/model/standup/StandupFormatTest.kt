package com.collabsphere.app.model.standup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StandupFormatTest {

    @Test
    fun editableRoundTripStripsBulletsAndBlankLines() {
        val editable = StandupFormat.toEditable(listOf("Completed A", "Merged PR #1"))
        assertEquals("• Completed A\n• Merged PR #1", editable)
        assertEquals(
            listOf("Completed A", "Merged PR #1", "typed by hand", "dash item"),
            StandupFormat.parseEditable("$editable\n\n   typed by hand  \n- dash item")
        )
    }

    @Test
    fun composesMarkdownWithNoneForEmptyBlockers() {
        val text = StandupFormat.compose("Thu, 16 Oct", "• Shipped login", "• Write tests", "")
        assertEquals(
            "**Standup · Thu, 16 Oct**\n**Yesterday**\n• Shipped login\n**Today**\n• Write tests\n**Blockers**\n• None",
            text
        )
    }

    @Test
    fun skipsEmptySectionAndRefusesWhenNothingToShare() {
        val text = StandupFormat.compose("Mon", "", "• Plan sprint", "• Waiting on design")!!
        assertTrue(!text.contains("Yesterday"))
        assertTrue(text.endsWith("**Blockers**\n• Waiting on design"))
        assertNull(StandupFormat.compose("Mon", "  ", "\n", "• only blockers"))
    }

    @Test
    fun capsLength() {
        val huge = (1..500).joinToString("\n") { "• item number $it with some words" }
        assertEquals(StandupFormat.MAX_LENGTH, StandupFormat.compose("Mon", huge, "", "")!!.length)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int = 10) =
        Calendar.getInstance().apply { clear(); set(year, month, day, hour, 30) }

    @Test
    fun previousWorkdaySkipsWeekend() {
        val monday = at(2026, Calendar.OCTOBER, 19)
        assertEquals(at(2026, Calendar.OCTOBER, 16, 0).apply { set(Calendar.MINUTE, 0) }.timeInMillis, StandupFormat.previousWorkdayStart(monday))
        val thursday = at(2026, Calendar.OCTOBER, 15)
        assertEquals(at(2026, Calendar.OCTOBER, 14, 0).apply { set(Calendar.MINUTE, 0) }.timeInMillis, StandupFormat.previousWorkdayStart(thursday))
        val sunday = at(2026, Calendar.OCTOBER, 18)
        assertEquals(at(2026, Calendar.OCTOBER, 16, 0).apply { set(Calendar.MINUTE, 0) }.timeInMillis, StandupFormat.previousWorkdayStart(sunday))
    }
}
