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
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.Properties

object EmailService {
    // --- Gmail API (OAuth2 over HTTPS Port 443) Configuration ---
    private var isGmailApiConfigured = false
    private var gmailClientId = ""
    private var gmailClientSecret = ""
    private var gmailRefreshToken = ""
    private var gmailSender = "collabsphere.studio@gmail.com"

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build()
    }

    // Cached access token for Gmail API
    @Volatile
    private var cachedAccessToken: String? = null
    @Volatile
    private var tokenExpiresAtMillis: Long = 0L

    private val accessTokenRegex = """"access_token"\s*:\s*"([^"]+)"""".toRegex()
    private val expiresInRegex = """"expires_in"\s*:\s*([0-9]+)""".toRegex()

    // --- Legacy SMTP Configuration (Fallback) ---
    private val smtpProperties = Properties()
    private var isSmtpConfigured = false
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

        // 2. Load Gmail API credentials
        gmailClientId = System.getenv("GMAIL_CLIENT_ID") ?: fileProps.getProperty("GMAIL_CLIENT_ID") ?: ""
        gmailClientSecret = System.getenv("GMAIL_CLIENT_SECRET") ?: fileProps.getProperty("GMAIL_CLIENT_SECRET") ?: ""
        gmailRefreshToken = System.getenv("GMAIL_REFRESH_TOKEN") ?: fileProps.getProperty("GMAIL_REFRESH_TOKEN") ?: ""
        gmailSender = System.getenv("GMAIL_SENDER")
            ?: fileProps.getProperty("GMAIL_SENDER")
            ?: System.getenv("SMTP_USER")
            ?: fileProps.getProperty("SMTP_USER")
            ?: "collabsphere.studio@gmail.com"

        if (gmailClientId.isNotBlank() && gmailClientSecret.isNotBlank() && gmailRefreshToken.isNotBlank()) {
            isGmailApiConfigured = true
            println("[EmailService] Configured Gmail API (OAuth2 HTTPS Port 443) for $gmailSender")
        }

        // 3. Load SMTP credentials (as secondary fallback)
        smtpHost = System.getenv("SMTP_HOST") ?: fileProps.getProperty("SMTP_HOST") ?: ""
        smtpPort = (System.getenv("SMTP_PORT") ?: fileProps.getProperty("SMTP_PORT") ?: "587").toIntOrNull() ?: 587
        smtpUser = System.getenv("SMTP_USER") ?: fileProps.getProperty("SMTP_USER") ?: ""
        smtpPass = System.getenv("SMTP_PASSWORD") ?: System.getenv("SMTP_PASS") ?: fileProps.getProperty("SMTP_PASSWORD") ?: fileProps.getProperty("SMTP_PASS") ?: ""
        smtpFrom = System.getenv("SMTP_FROM") ?: fileProps.getProperty("SMTP_FROM") ?: (if (smtpUser.isNotBlank()) "CollabSphere <$smtpUser>" else "CollabSphere <$gmailSender>")

        if (smtpHost.isNotBlank() && smtpUser.isNotBlank() && smtpPass.isNotBlank()) {
            smtpProperties["mail.smtp.host"] = smtpHost
            smtpProperties["mail.smtp.port"] = smtpPort.toString()
            smtpProperties["mail.smtp.auth"] = "true"
            smtpProperties["mail.smtp.starttls.enable"] = "true"
            smtpProperties["mail.smtp.ssl.protocols"] = "TLSv1.2 TLSv1.3"
            if (smtpPort == 465) {
                smtpProperties["mail.smtp.socketFactory.port"] = "465"
                smtpProperties["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
            }
            isSmtpConfigured = true
            println("[EmailService] Configured SMTP fallback: host=$smtpHost, port=$smtpPort, user=$smtpUser")
        }

        if (!isGmailApiConfigured && !isSmtpConfigured) {
            println("[EmailService] No email provider fully configured. Emails will be logged to stdout.")
        }
    }

    /**
     * Obtains or refreshes the OAuth2 access token from Google's token endpoint.
     */
    @Synchronized
    private fun getValidAccessToken(): String {
        val now = System.currentTimeMillis()
        if (cachedAccessToken != null && now < tokenExpiresAtMillis) {
            return cachedAccessToken!!
        }

        println("[EmailService] Requesting fresh Google OAuth2 access token...")
        val formBody = listOf(
            "client_id" to gmailClientId,
            "client_secret" to gmailClientSecret,
            "refresh_token" to gmailRefreshToken,
            "grant_type" to "refresh_token"
        ).joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://oauth2.googleapis.com/token"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Failed to refresh Gmail OAuth token (HTTP ${response.statusCode()}): ${response.body()}")
        }

        val token = accessTokenRegex.find(response.body())?.groupValues?.get(1)
            ?: throw IllegalStateException("Failed to parse access_token from Google OAuth response: ${response.body()}")
        val expiresIn = expiresInRegex.find(response.body())?.groupValues?.get(1)?.toLongOrNull() ?: 3600L

        cachedAccessToken = token
        // Buffer by 60 seconds before expiration
        tokenExpiresAtMillis = now + ((expiresIn - 60).coerceAtLeast(300) * 1000L)
        println("[EmailService] Successfully refreshed Google access token (valid for ${expiresIn}s)")
        return token
    }

    /**
     * Sends an email via Google Gmail REST API over HTTPS (Port 443).
     */
    private fun sendViaGmailApi(to: String, subject: String, htmlBody: String): Boolean {
        val token = getValidAccessToken()

        // Build RFC-822 MIME message
        val session = Session.getInstance(Properties())
        val message = MimeMessage(session).apply {
            val fromAddress = if (gmailSender.contains("<") && gmailSender.contains(">")) {
                InternetAddress(gmailSender)
            } else {
                InternetAddress(gmailSender, "CollabSphere")
            }
            setFrom(fromAddress)
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            setSubject(subject, "UTF-8")
            setContent(htmlBody, "text/html; charset=utf-8")

            // Transactional priority headers
            addHeader("X-Priority", "1")
            addHeader("Priority", "urgent")
            addHeader("Importance", "high")
            addHeader("X-MSMail-Priority", "High")
        }

        val baos = ByteArrayOutputStream()
        message.writeTo(baos)
        val rawBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray())

        val payload = """{"raw":"$rawBase64"}"""

        val sendRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val sendResponse = httpClient.send(sendRequest, HttpResponse.BodyHandlers.ofString())
        if (sendResponse.statusCode() in 200..299) {
            println("[EmailService] Gmail API: Successfully sent email to $to: \"$subject\"")
            return true
        } else {
            System.err.println("[EmailService] Gmail API send error HTTP ${sendResponse.statusCode()}: ${sendResponse.body()}")
            return false
        }
    }

    /**
     * Sends an email via legacy SMTP (Port 587/465).
     */
    private fun sendViaSmtp(to: String, subject: String, htmlBody: String): Boolean {
        val session = Session.getInstance(smtpProperties, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(smtpUser, smtpPass)
            }
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(smtpFrom))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            setSubject(subject, "UTF-8")
            setContent(htmlBody, "text/html; charset=utf-8")
            addHeader("X-Priority", "1")
            addHeader("Importance", "high")
        }

        Transport.send(message)
        println("[EmailService] SMTP: Successfully sent email to $to: \"$subject\"")
        return true
    }

    /**
     * Primary dispatch method with 3-tier fallback:
     * Tier 1: Gmail API via OAuth2 (Port 443 HTTPS)
     * Tier 2: Standard SMTP (Port 587 / 465)
     * Tier 3: Console Mock Print
     */
    suspend fun sendEmail(to: String, subject: String, htmlBody: String): Boolean = withContext(Dispatchers.IO) {
        // Tier 1: Gmail API
        if (isGmailApiConfigured) {
            try {
                val ok = sendViaGmailApi(to, subject, htmlBody)
                if (ok) return@withContext true
                println("[EmailService] Gmail API returned unsuccessful response, falling back...")
            } catch (e: Exception) {
                System.err.println("[EmailService] Gmail API failed: ${e.message}, falling back...")
                e.printStackTrace()
            }
        }

        // Tier 2: SMTP Fallback
        if (isSmtpConfigured) {
            try {
                val ok = sendViaSmtp(to, subject, htmlBody)
                if (ok) return@withContext true
                println("[EmailService] SMTP dispatch failed, falling back...")
            } catch (e: Exception) {
                System.err.println("[EmailService] SMTP failed: ${e.message}")
                e.printStackTrace()
            }
        }

        // Tier 3: Console Mock Output
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
