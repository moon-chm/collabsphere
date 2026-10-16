package com.collabsphere.app.model.commitment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitmentDetectorTest {

    private val wednesday = 3

    private fun detect(text: String, today: Int = wednesday) = CommitmentDetector.detect(text, today)

    @Test
    fun catchesPromiseWithWeekdayDeadline() {
        val text = "Sure, I'll fix the login bug by Friday."
        val c = detect(text)
        assertNotNull(c)
        c!!
        assertEquals("Fix the login bug", c.title)
        assertEquals(2, c.dueInDays)
        assertEquals("I'll fix the login bug by Friday", text.substring(c.start, c.end))
    }

    @Test
    fun catchesPromiseWithoutDeadline() {
        val c = detect("I will update the onboarding screens")!!
        assertEquals("Update the onboarding screens", c.title)
        assertNull(c.dueInDays)
    }

    @Test
    fun acceptsCurlyApostropheAndMixedCase() {
        val c = detect("ok I’LL Review the API docs tomorrow")!!
        assertEquals("Review the API docs", c.title)
        assertEquals(1, c.dueInDays)
    }

    @Test
    fun relativeDeadlines() {
        assertEquals(0, detect("I'll send the report today")!!.dueInDays)
        assertEquals(0, detect("I'll send the report by EOD")!!.dueInDays)
        assertEquals(0, detect("I'll send the report by end of the day")!!.dueInDays)
        assertEquals(1, detect("I'm going to deploy the build tmrw")!!.dueInDays)
        assertEquals(3, detect("I'll ship the release in 3 days")!!.dueInDays)
        assertEquals(14, detect("I'll migrate the database in two weeks")!!.dueInDays)
        assertEquals(5, detect("I'll write the tests next week")!!.dueInDays)
        assertEquals(2, detect("I'll finish the design end of week")!!.dueInDays)
        assertEquals(3, detect("I'll clean the backlog this weekend")!!.dueInDays)
    }

    @Test
    fun weekdayEqualToTodayMeansToday_unlessNext() {
        assertEquals(0, detect("I'll merge the PR on Wednesday")!!.dueInDays)
        assertEquals(7, detect("I'll merge the PR next Wednesday")!!.dueInDays)
        assertEquals(6, detect("I'll merge the PR by Tuesday")!!.dueInDays)
    }

    @Test
    fun weekendFromSunday() {
        assertEquals(0, detect("I'll clean the backlog this weekend", today = 7)!!.dueInDays)
        assertEquals(1, detect("I'll clean the backlog this weekend", today = 5)!!.dueInDays)
    }

    @Test
    fun shortVagueActionNeedsDeadline() {
        assertNull(detect("I'll fix it"))
        assertNull(detect("I'll do it"))
        val c = detect("I'll fix it by tomorrow")!!
        assertEquals("Fix it", c.title)
    }

    @Test
    fun ignoresNegationsAndNonTasks() {
        assertNull(detect("I won't fix the login bug"))
        assertNull(detect("I will not fix the login bug"))
        assertNull(detect("I'll be late for the standup"))
        assertNull(detect("Let me know when the build is ready"))
        assertNull(detect("I'll probably skip the meeting"))
        assertNull(detect("I'll have the numbers tomorrow"))
    }

    @Test
    fun ignoresQuestions() {
        assertNull(detect("Should I say I'll fix the login bug?"))
        assertNull(detect("I'll handle the release notes?"))
    }

    @Test
    fun ignoresOtherPeoplesPromises() {
        assertNull(detect("You'll fix the login bug by Friday"))
        assertNull(detect("He will update the onboarding screens"))
    }

    @Test
    fun letMeAndGonna() {
        assertEquals("Check the crash logs", detect("let me check the crash logs today")!!.title)
        assertEquals("Refactor the sync worker", detect("I'm gonna refactor the sync worker")!!.title)
    }

    @Test
    fun stopsAtSentenceEnd() {
        val text = "I'll update the README. Tomorrow we demo."
        val c = detect(text)!!
        assertEquals("Update the README", c.title)
        assertNull(c.dueInDays)
        assertEquals("I'll update the README", text.substring(c.start, c.end))
    }

    @Test
    fun skipsRejectedTriggerAndFindsLaterOne() {
        val c = detect("I'll be quick. I will rewrite the payment flow on Monday")!!
        assertEquals("Rewrite the payment flow", c.title)
        assertEquals(5, c.dueInDays)
    }

    @Test
    fun possessiveWeekdayIsNotADeadline() {
        val c = detect("I'll prepare the slides for Monday's review")!!
        assertNull(c.dueInDays)
        assertEquals("Prepare the slides for Monday's review", c.title)
    }

    @Test
    fun titleIsCappedAndWordsLikeSunAreNotDays() {
        assertNull(detect("I'll enjoy the sun")?.dueInDays)
        val long = "I'll " + "refactor ".repeat(20) + "everything"
        assertTrue(detect(long)!!.title.length <= 80)
    }

    @Test
    fun ignoresTextWithoutTrigger() {
        assertNull(detect(""))
        assertNull(detect("The login bug is fixed"))
        assertNull(detect("Mail me the report by Friday"))
    }
}
