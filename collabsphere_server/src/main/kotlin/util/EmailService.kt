package com.collabsphere.util

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

object EmailService {
    private val properties = Properties()
    private var isConfigured = false
    private var smtpHost = ""
    private var smtpPort = 587
    private var smtpUser = ""
    private var smtpPass = ""
    private var smtpFrom = ""

    init {
        // 1. Try reading from local.properties if present
        val localPropsFile = File("local.properties")
        val fileProps = Properties()
        if (localPropsFile.exists()) {
            try {
                localPropsFile.inputStream().use { fileProps.load(it) }
            } catch (_: Exception) {}
        }

        smtpHost = System.getenv("SMTP_HOST") ?: fileProps.getProperty("SMTP_HOST") ?: ""
        smtpPort = (System.getenv("SMTP_PORT") ?: fileProps.getProperty("SMTP_PORT") ?: "587").toIntOrNull() ?: 587
        smtpUser = System.getenv("SMTP_USER") ?: fileProps.getProperty("SMTP_USER") ?: ""
        smtpPass = System.getenv("SMTP_PASSWORD") ?: System.getenv("SMTP_PASS") ?: fileProps.getProperty("SMTP_PASSWORD") ?: fileProps.getProperty("SMTP_PASS") ?: ""
        smtpFrom = System.getenv("SMTP_FROM") ?: fileProps.getProperty("SMTP_FROM") ?: (if (smtpUser.isNotBlank()) "CollabSphere <$smtpUser>" else "CollabSphere <support@collabsphere.com>")

        if (smtpHost.isNotBlank() && smtpUser.isNotBlank() && smtpPass.isNotBlank()) {
            properties["mail.smtp.host"] = smtpHost
            properties["mail.smtp.port"] = smtpPort.toString()
            properties["mail.smtp.auth"] = "true"
            properties["mail.smtp.starttls.enable"] = "true"
            properties["mail.smtp.ssl.protocols"] = "TLSv1.2 TLSv1.3"
            if (smtpPort == 465) {
                properties["mail.smtp.socketFactory.port"] = "465"
                properties["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
            }
            isConfigured = true
            println("[EmailService] Configured SMTP: host=$smtpHost, port=$smtpPort, user=$smtpUser")
        } else {
            println("[EmailService] SMTP not fully configured. Emails will be logged to stdout.")
        }
    }

    suspend fun sendEmail(to: String, subject: String, htmlBody: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            println("""
                ============================================================
                [MOCK EMAIL DISPATCH]
                To: $to
                Subject: $subject
                Body:
                $htmlBody
                ============================================================
            """.trimIndent())
            return@withContext true
        }

        try {
            val session = Session.getInstance(properties, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(smtpUser, smtpPass)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(smtpFrom))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject, "UTF-8")
                setContent(htmlBody, "text/html; charset=utf-8")
            }

            Transport.send(message)
            println("[EmailService] Successfully sent email to $to: \"$subject\"")
            true
        } catch (e: Exception) {
            println("[EmailService] FAILED to send email to $to: ${e.message}")
            e.printStackTrace()
            // Fallback: log email content so testing never breaks
            println("[EmailService] Fallback print: To=$to, Subject=$subject, Content=\n$htmlBody")
            false
        }
    }

    suspend fun sendVerificationOtp(to: String, otp: String): Boolean {
        val subject = "$otp is your CollabSphere verification code"
        val html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"></head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #E8E4DA; margin: 0; padding: 32px;">
                <div style="max-width: 520px; margin: 0 auto; background: #FDFBF7; border-radius: 20px; padding: 36px; box-shadow: 0 4px 16px rgba(44,42,40,0.12); border: 1px solid rgba(44,42,40,0.08);">
                    <div style="text-align: center; margin-bottom: 24px;">
                        <span style="font-size: 26px; font-weight: 800; color: #F0633D;">CollabSphere</span>
                    </div>
                    <h2 style="color: #2C2A28; font-size: 22px; font-weight: 700; margin-bottom: 12px; text-align: center;">Verify your email address</h2>
                    <p style="color: #7A7570; font-size: 15px; line-height: 1.5; text-align: center;">
                        Thank you for joining CollabSphere. Enter the following 6-digit verification code in the app to activate your account:
                    </p>
                    <div style="background: #FAF8F3; border: 2px solid #F0633D; border-radius: 14px; padding: 18px; text-align: center; margin: 28px 0;">
                        <span style="font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #2C2A28;">$otp</span>
                    </div>
                    <p style="color: #7A7570; font-size: 13px; text-align: center;">
                        This code will expire in <strong>15 minutes</strong>. If you did not create a CollabSphere account, please disregard this email.
                    </p>
                </div>
            </body>
            </html>
        """.trimIndent()
        return sendEmail(to, subject, html)
    }

    suspend fun sendPasswordResetOtp(to: String, otp: String): Boolean {
        val subject = "$otp is your CollabSphere password reset code"
        val html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"></head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #E8E4DA; margin: 0; padding: 32px;">
                <div style="max-width: 520px; margin: 0 auto; background: #FDFBF7; border-radius: 20px; padding: 36px; box-shadow: 0 4px 16px rgba(44,42,40,0.12); border: 1px solid rgba(44,42,40,0.08);">
                    <div style="text-align: center; margin-bottom: 24px;">
                        <span style="font-size: 26px; font-weight: 800; color: #F0633D;">CollabSphere</span>
                    </div>
                    <h2 style="color: #2C2A28; font-size: 22px; font-weight: 700; margin-bottom: 12px; text-align: center;">Reset your password</h2>
                    <p style="color: #7A7570; font-size: 15px; line-height: 1.5; text-align: center;">
                        We received a request to reset your CollabSphere password. Use the following 6-digit code in the app to set a new password:
                    </p>
                    <div style="background: #FAF8F3; border: 2px solid #4C55C4; border-radius: 14px; padding: 18px; text-align: center; margin: 28px 0;">
                        <span style="font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #2C2A28;">$otp</span>
                    </div>
                    <p style="color: #7A7570; font-size: 13px; text-align: center;">
                        This code will expire in <strong>15 minutes</strong>. If you did not request a password reset, you can safely ignore this email.
                    </p>
                </div>
            </body>
            </html>
        """.trimIndent()
        return sendEmail(to, subject, html)
    }

    suspend fun sendWorkspaceInvitation(to: String, workspaceName: String, inviterName: String, inviteCode: String): Boolean {
        val subject = "$inviterName invited you to join $workspaceName on CollabSphere"
        val html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"></head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #E8E4DA; margin: 0; padding: 32px;">
                <div style="max-width: 520px; margin: 0 auto; background: #FDFBF7; border-radius: 20px; padding: 36px; box-shadow: 0 4px 16px rgba(44,42,40,0.12); border: 1px solid rgba(44,42,40,0.08);">
                    <div style="text-align: center; margin-bottom: 24px;">
                        <span style="font-size: 26px; font-weight: 800; color: #F0633D;">CollabSphere</span>
                    </div>
                    <h2 style="color: #2C2A28; font-size: 22px; font-weight: 700; margin-bottom: 12px; text-align: center;">You've been invited!</h2>
                    <p style="color: #7A7570; font-size: 15px; line-height: 1.5; text-align: center;">
                        <strong>$inviterName</strong> has invited you to collaborate in the <strong>$workspaceName</strong> workspace.
                    </p>
                    <div style="background: #FAF8F3; border: 2px solid #4E9E78; border-radius: 14px; padding: 18px; text-align: center; margin: 28px 0;">
                        <p style="color: #7A7570; font-size: 12px; text-transform: uppercase; font-weight: 700; margin: 0 0 6px 0; letter-spacing: 1px;">Your Invite Code</p>
                        <span style="font-size: 32px; font-weight: 800; letter-spacing: 6px; color: #2C2A28;">$inviteCode</span>
                    </div>
                    <p style="color: #7A7570; font-size: 14px; text-align: center;">
                        Open CollabSphere and tap <strong>"Join with Code"</strong> on your Dashboard to join this workspace!
                    </p>
                </div>
            </body>
            </html>
        """.trimIndent()
        return sendEmail(to, subject, html)
    }
}
