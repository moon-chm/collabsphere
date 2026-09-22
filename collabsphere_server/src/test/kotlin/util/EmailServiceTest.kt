package util

import com.collabsphere.util.EmailService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class EmailServiceTest {
    @Test
    fun testSendVerificationOtp() = runBlocking {
        val testRecipient = "collabsphere.studio@gmail.com"
        val success = EmailService.sendVerificationOtp(testRecipient, "123456")
        assertTrue(success, "EmailService should successfully send email via Gmail API")
    }
}
