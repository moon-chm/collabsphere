package com.collabsphere.app.view.components

import com.collabsphere.app.dto.message.ChannelReadState
import com.collabsphere.app.model.message.MessageEntity
import com.collabsphere.app.model.task.TaskEntity
import com.collabsphere.app.model.task.TaskPriority
import com.collabsphere.app.view.summarizeMyDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownAndReadsTest {

    @Test
    fun parsesInlineStylesAndLinks() {
        val spans = parseMarkdown("Hi **bold** and *it* with `code` ~~old~~ see https://example.com/a.")
        assertEquals(
            listOf(
                MdSpan("Hi "),
                MdSpan("bold", setOf(MdStyle.BOLD)),
                MdSpan(" and "),
                MdSpan("it", setOf(MdStyle.ITALIC)),
                MdSpan(" with "),
                MdSpan("code", setOf(MdStyle.CODE)),
                MdSpan(" "),
                MdSpan("old", setOf(MdStyle.STRIKE)),
                MdSpan(" see "),
                MdSpan("https://example.com/a", setOf(MdStyle.LINK), url = "https://example.com/a"),
                MdSpan(".")
            ),
            spans
        )
    }

    @Test
    fun leavesSnakeCaseAndArithmeticAlone() {
        assertEquals(listOf(MdSpan("use snake_case_names and 2*3*4")), parseMarkdown("use snake_case_names and 2*3*4"))
    }

    @Test
    fun parsesFencedCodeBlocks() {
        val spans = parseMarkdown("Run:\n```\nval x = 1\n```")
        assertEquals(MdSpan("val x = 1", setOf(MdStyle.CODE_BLOCK)), spans.last())
        assertEquals("Run:\nval x = 1", markdownToPlainText("Run:\n```\nval x = 1\n```"))
    }

    @Test
    fun extractsFirstUrlWithoutTrailingPunctuation() {
        assertEquals("https://a.dev/x?y=1", extractFirstUrl("see https://a.dev/x?y=1, then http://b.dev"))
        assertNull(extractFirstUrl("no links here"))
    }

    @Test
    fun seenByExcludesAuthorAndSelfAndSummarisesLongLists() {
        val message = MessageEntity(id = 10, userId = 1, workspaceId = 1, channelId = 1, userName = "Author", content = "hi")
        fun state(userId: Int, name: String, last: Int) = ChannelReadState(workspaceId = 1, channelId = 1, userId = userId, userName = name, lastReadMessageId = last)
        val states = listOf(state(1, "Author", 10), state(2, "Me", 10), state(3, "Ben", 10), state(4, "Ana", 12), state(5, "Old", 9))
        assertEquals("Seen by Ana, Ben", seenByLabel(states, message, currentUserId = 2))
        val many = states + listOf(state(6, "Cy", 10), state(7, "Di", 11))
        assertEquals("Seen by Ana, Ben +2", seenByLabel(many, message, currentUserId = 2))
        assertNull(seenByLabel(listOf(state(5, "Old", 9)), message, currentUserId = 2))
    }

    @Test
    fun myDaySplitsOverdueTodayAndHighPriority() {
        val day = 24 * 60 * 60 * 1000L
        val today = 20_000 * day
        fun task(id: Int, due: Long?, priority: TaskPriority = TaskPriority.MEDIUM) = TaskEntity(
            id = id, createdByUserId = 1, assignedToUserId = 1, workspaceId = 1,
            taskName = "t$id", taskDescription = "", dueDate = due, priority = priority
        )
        val summary = summarizeMyDay(
            listOf(task(1, today - day), task(2, today), task(3, today + 3 * day, TaskPriority.HIGH), task(4, null), task(5, today, TaskPriority.HIGH)),
            today
        )
        assertEquals(listOf(1), summary.overdue.map { it.id })
        assertEquals(listOf(2, 5), summary.dueToday.map { it.id })
        assertEquals(listOf(5, 3), summary.highPriority.map { it.id })
        assertEquals(listOf(1, 2, 5, 3), summary.focus.map { it.id })
    }
}
