package com.collabsphere.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailTemplatesTest {

    @Test
    fun `escape neutralises every HTML-significant character`() {
        assertEquals("&lt;b&gt;&quot;Tom&quot; &amp; &#39;Jerry&#39;&lt;/b&gt;", EmailTemplates.escape("<b>\"Tom\" & 'Jerry'</b>"))
    }

    @Test
    fun `invite email never renders user-supplied HTML`() {
        val email = EmailTemplates.workspaceInvitation(
            workspaceName = "<script>alert(1)</script>Design",
            inviterName = "<img src=x onerror=alert(2)>",
            inviteCode = "AB12CD",
            validDays = 7
        )
        assertFalse(email.html.contains("<script>"))
        assertFalse(email.html.contains("<img"))
        assertTrue(email.html.contains("&lt;script&gt;"))
        assertTrue(email.html.contains("AB12CD"))
        assertTrue(email.html.contains("7 days"))
    }

    @Test
    fun `subjects cannot carry line breaks into mail headers`() {
        val email = EmailTemplates.workspaceInvitation("Team\r\nBcc: victim@example.com", "Ann\nB", "AB12CD", 7)
        assertFalse(email.subject.contains('\n'))
        assertFalse(email.subject.contains('\r'))
    }

    @Test
    fun `code emails carry the code in subject, html and plain text`() {
        listOf(
            EmailTemplates.verification("482913", 15),
            EmailTemplates.passwordReset("482913", 15),
            EmailTemplates.emailChange("482913", "new@example.com", 15)
        ).forEach { email ->
            assertTrue(email.subject.startsWith("482913"))
            assertTrue(email.html.contains("482913"))
            assertTrue(email.text.contains("Your code: 482913"))
            assertTrue(email.text.contains("15 minutes"))
            assertFalse(email.text.contains("<"), "plain text part must not contain markup")
        }
    }

    @Test
    fun `email change wording is not the sign-up wording`() {
        val change = EmailTemplates.emailChange("111111", "new@example.com", 15)
        assertTrue(change.html.contains("new@example.com"))
        assertFalse(change.html.contains("Welcome to CollabSphere"))
        assertTrue(EmailTemplates.verification("111111", 15).html.contains("Welcome to CollabSphere"))
    }

    @Test
    fun `layout includes a hidden preheader and a single card`() {
        val html = EmailTemplates.verification("123456", 15).html
        assertTrue(html.contains("display: none"))
        assertTrue(html.contains("Enter this code to finish creating your account"))
        assertTrue(html.trimStart().startsWith("<!DOCTYPE html>"))
    }

    @Test
    fun `tactile depth degrades to bevel borders and solid colours where shadows are unsupported`() {
        val html = EmailTemplates.workspaceInvitation("Design", "Ann", "AB12CD", 7).html
        val card = Regex("class=\"cs-card\" style=\"([^\"]+)\"").find(html)!!.groupValues[1]
        val well = Regex("class=\"cs-well\" style=\"([^\"]+)\"").find(html)!!.groupValues[1]
        listOf(card, well).forEach { style ->
            assertTrue(style.contains("box-shadow"))
            assertTrue(style.contains("background-color"), "gradient needs a solid fallback")
            assertTrue(style.contains("border-top-color") && style.contains("border-bottom-color"), "bevel borders must back up the shadow")
        }
        assertTrue(html.contains("bgcolor=\"#FDFBF7\""))
        assertTrue(html.contains("@media only screen and (max-width: 480px)"))
    }
}

