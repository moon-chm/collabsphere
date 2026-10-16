package com.collabsphere.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailServicePolicyTest {

    @Test
    fun `console fallback is only for local development with no provider`() {
        assertTrue(EmailService.consoleFallbackAllowed(gmailConfigured = false, smtpConfigured = false, environment = emptyMap()))
        assertFalse(EmailService.consoleFallbackAllowed(gmailConfigured = true, smtpConfigured = false, environment = emptyMap()))
        assertFalse(EmailService.consoleFallbackAllowed(gmailConfigured = false, smtpConfigured = true, environment = emptyMap()))
        assertFalse(EmailService.consoleFallbackAllowed(gmailConfigured = false, smtpConfigured = false, environment = mapOf("APP_ENV" to "production")))
        assertFalse(EmailService.consoleFallbackAllowed(gmailConfigured = false, smtpConfigured = false, environment = mapOf("APP_ENV" to "Production")))
    }

    @Test
    fun `log lines mask the recipient address`() {
        assertEquals("r***@gmail.com", EmailService.maskEmail("rohit@gmail.com"))
        assertEquals("***", EmailService.maskEmail("not-an-email"))
        assertEquals("***", EmailService.maskEmail("@nouser.com"))
    }
}
