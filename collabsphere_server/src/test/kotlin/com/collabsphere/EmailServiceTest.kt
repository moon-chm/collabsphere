package com.collabsphere

import com.collabsphere.util.EmailService
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue

class EmailServiceTest {
    @Test
    fun testEmailService() = runBlocking {
        println("Testing EmailService...")
        val result = EmailService.sendEmail("test@example.com", "Test Subject", "<p>Test Body</p>")
        assertTrue(result, "Email should be sent (or fallback handled gracefully)")
    }
}
