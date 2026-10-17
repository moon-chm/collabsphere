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
        assertFalse(email.html.contains("<img src=x"))
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
    fun `print card keeps its ink outline and offset shadow without css shadows`() {
        val html = EmailTemplates.workspaceInvitation("Design", "Ann", "AB12CD", 7).html
        val card = Regex("class=\"cs-card\" style=\"([^\"]+)\"").find(html)!!.groupValues[1]
        val well = Regex("class=\"cs-well\" style=\"([^\"]+)\"").find(html)!!.groupValues[1]
        assertTrue(card.contains("border: 2px solid #1E2A5A"))
        assertTrue(card.contains("border-bottom-width: 6px") && card.contains("border-right-width: 6px"))
        assertTrue(well.contains("border: 2px solid #1E2A5A"))
        assertFalse(html.contains("box-shadow"))
        assertFalse(html.contains("linear-gradient"))
        assertTrue(html.contains("bgcolor=\"#FFFFFF\""))
        assertTrue(html.contains("@media only screen and (max-width: 480px)"))
    }

    @Test
    fun `every email shows the logo as an inline image`() {
        listOf(
            EmailTemplates.verification("123456", 15),
            EmailTemplates.passwordReset("123456", 15),
            EmailTemplates.emailChange("123456", "new@example.com", 15),
            EmailTemplates.workspaceInvitation("Design", "Ann", "AB12CD", 7)
        ).forEach { email ->
            assertTrue(email.html.contains("src=\"cid:${EmailTemplates.LOGO_CID}\""))
            assertTrue(email.html.contains("alt=\"CollabSphere\""))
        }
    }

    @Test
    fun `mime message embeds the logo next to the html part`() {
        val email = EmailTemplates.verification("123456", 15)
        val message = jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(java.util.Properties()))
        message.setContent(EmailService.alternativeContent(email.html, email.text))
        message.saveChanges()
        val out = java.io.ByteArrayOutputStream()
        message.writeTo(out)
        val raw = out.toString(Charsets.UTF_8)
        assertTrue(raw.contains("multipart/related"))
        assertTrue(raw.contains("Content-ID: <${EmailTemplates.LOGO_CID}>"))
        assertTrue(raw.contains("image/png"))
        assertTrue(raw.length < 40_000, "email grew to ${raw.length} bytes")
    }
}
