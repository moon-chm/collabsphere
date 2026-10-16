package com.collabsphere.util

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
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
import org.slf4j.LoggerFactory

object EmailService {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)
    // --- Gmail API (OAuth2 over HTTPS Port 443) Configuration ---
    private var isGmailApiConfigured = false
    private var gmailCredentialsPresent = false
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
        val externalProvidersDisabled = ExternalProviderPolicy.areDisabled()
        // Force IPv4 for SMTP connections to prevent SocketTimeoutException on networks with broken IPv6 routing
        System.setProperty("java.net.preferIPv4Stack", "true")

        // 1. Try reading from local.properties if present
        val localPropsFile = File("local.properties")
        val fileProps = Properties()
        if (!externalProvidersDisabled && localPropsFile.exists()) {
            try {
                localPropsFile.inputStream().use { fileProps.load(it) }
            } catch (_: Exception) {}
        }

        // 2. Load Gmail API credentials
        gmailClientId = if (externalProvidersDisabled) "" else System.getenv("GMAIL_CLIENT_ID") ?: fileProps.getProperty("GMAIL_CLIENT_ID") ?: ""
        gmailClientSecret = if (externalProvidersDisabled) "" else System.getenv("GMAIL_CLIENT_SECRET") ?: fileProps.getProperty("GMAIL_CLIENT_SECRET") ?: ""
        gmailRefreshToken = if (externalProvidersDisabled) "" else System.getenv("GMAIL_REFRESH_TOKEN") ?: fileProps.getProperty("GMAIL_REFRESH_TOKEN") ?: ""
        gmailSender = System.getenv("GMAIL_SENDER")
            ?: fileProps.getProperty("GMAIL_SENDER")
            ?: System.getenv("SMTP_USER")
            ?: fileProps.getProperty("SMTP_USER")
            ?: "collabsphere.studio@gmail.com"

        if (gmailClientId.isNotBlank() && gmailClientSecret.isNotBlank() && gmailRefreshToken.isNotBlank()) {
            isGmailApiConfigured = true
            gmailCredentialsPresent = true
            logger.info("[EmailService] Configured Gmail API (OAuth2 HTTPS Port 443) for $gmailSender")
        }

        // 3. Load SMTP credentials (as secondary fallback)
        smtpHost = if (externalProvidersDisabled) "" else System.getenv("SMTP_HOST") ?: fileProps.getProperty("SMTP_HOST") ?: ""
        smtpPort = if (externalProvidersDisabled) 587 else (System.getenv("SMTP_PORT") ?: fileProps.getProperty("SMTP_PORT") ?: "587").toIntOrNull() ?: 587
        smtpUser = if (externalProvidersDisabled) "" else System.getenv("SMTP_USER") ?: fileProps.getProperty("SMTP_USER") ?: ""
        smtpPass = if (externalProvidersDisabled) "" else System.getenv("SMTP_PASSWORD") ?: System.getenv("SMTP_PASS") ?: fileProps.getProperty("SMTP_PASSWORD") ?: fileProps.getProperty("SMTP_PASS") ?: ""
        smtpFrom = System.getenv("SMTP_FROM") ?: fileProps.getProperty("SMTP_FROM") ?: (if (smtpUser.isNotBlank()) "CollabSphere <$smtpUser>" else "CollabSphere <$gmailSender>")

        if (smtpHost.isNotBlank() && smtpUser.isNotBlank() && smtpPass.isNotBlank()) {
            smtpProperties["mail.smtp.host"] = smtpHost
            smtpProperties["mail.smtp.port"] = smtpPort.toString()
            smtpProperties["mail.smtp.auth"] = "true"
            smtpProperties["mail.smtp.starttls.enable"] = "true"
            smtpProperties["mail.smtp.ssl.protocols"] = "TLSv1.2 TLSv1.3"
            smtpProperties["mail.smtp.connectiontimeout"] = "5000"
            smtpProperties["mail.smtp.timeout"] = "5000"
            if (smtpPort == 465) {
                smtpProperties["mail.smtp.socketFactory.port"] = "465"
                smtpProperties["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
            }
            isSmtpConfigured = true
            logger.info("[EmailService] Configured SMTP fallback: host=$smtpHost, port=$smtpPort, user=$smtpUser")
        }

        if (!isGmailApiConfigured && !isSmtpConfigured) {
            logger.warn("[EmailService] No email provider configured. Outside production, emails are printed to stdout instead of sent.")
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

        logger.debug("[EmailService] Requesting fresh Google OAuth2 access token...")
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
        logger.info("[EmailService] Successfully refreshed Google access token (valid for ${expiresIn}s)")
        return token
    }

    /**
     * Sends an email via Google Gmail REST API over HTTPS (Port 443).
     */
    private fun alternativeContent(htmlBody: String, textBody: String): MimeMultipart =
        MimeMultipart("alternative").apply {
            addBodyPart(MimeBodyPart().apply { setText(textBody, "UTF-8", "plain") })
            addBodyPart(MimeBodyPart().apply { setText(htmlBody, "UTF-8", "html") })
        }

    private fun sendViaGmailApi(to: String, subject: String, htmlBody: String, textBody: String): Boolean {
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
            setContent(alternativeContent(htmlBody, textBody))
            addHeader("Auto-Submitted", "auto-generated")
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
            logger.info("[EmailService] Gmail API: email delivered to ${maskEmail(to)}")
            return true
        } else {
            logger.error("[EmailService] Gmail API send error HTTP ${sendResponse.statusCode()}: ${sendResponse.body()}")
            return false
        }
    }

    /**
     * Sends an email via legacy SMTP (Port 587/465).
     */
    private fun sendViaSmtp(to: String, subject: String, htmlBody: String, textBody: String): Boolean {
        val session = Session.getInstance(smtpProperties, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(smtpUser, smtpPass)
            }
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(smtpFrom))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            setSubject(subject, "UTF-8")
            setContent(alternativeContent(htmlBody, textBody))
            addHeader("Auto-Submitted", "auto-generated")
        }

        Transport.send(message)
        logger.info("[EmailService] SMTP: email delivered to ${maskEmail(to)}")
        return true
    }

    /**
     * Primary dispatch method with 3-tier fallback:
     * Tier 1: Gmail API via OAuth2 (Port 443 HTTPS)
     * Tier 2: Standard SMTP (Port 587 / 465)
     * Tier 3: Console Mock Print
     */
    suspend fun sendEmail(content: EmailContent, to: String): Boolean = sendEmail(to, content.subject, content.html, content.text)

    suspend fun sendEmail(to: String, subject: String, htmlBody: String, textBody: String): Boolean = withContext(Dispatchers.IO) {
        // Tier 1: Gmail API
        if (isGmailApiConfigured) {
            try {
                val ok = sendViaGmailApi(to, subject, htmlBody, textBody)
                if (ok) return@withContext true
                logger.warn("[EmailService] Gmail API returned unsuccessful response, falling back...")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e.message?.contains("invalid_grant") == true) {
                    isGmailApiConfigured = false
                    logger.error(
                        "[EmailService] Gmail refresh token is expired or revoked. Gmail sending is off until GMAIL_REFRESH_TOKEN " +
                            "is replaced and the server restarts. If the Google OAuth app is in Testing mode, tokens expire after 7 days; publish it to Production."
                    )
                } else {
                    logger.warn("[EmailService] Gmail API failed (${e.message}), falling back...")
                }
            }
        }

        // Tier 2: SMTP Fallback
        if (isSmtpConfigured) {
            try {
                val ok = sendViaSmtp(to, subject, htmlBody, textBody)
                if (ok) return@withContext true
                logger.warn("[EmailService] SMTP dispatch failed, falling back...")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.warn("[EmailService] SMTP failed (${e.message}), falling back...")
            }
        }

        if (!consoleFallbackAllowed(gmailConfigured = gmailCredentialsPresent, smtpConfigured = isSmtpConfigured)) {
            logger.error("[EmailService] Email to ${maskEmail(to)} was NOT delivered: every configured provider failed")
            return@withContext false
        }

        println("""
            ============================================================
            [DEV EMAIL - no provider configured, not sent]
            To: $to
            Subject: $subject
            Body:
            $textBody
            ============================================================
        """.trimIndent())
        return@withContext true
    }

    internal fun consoleFallbackAllowed(
        gmailConfigured: Boolean,
        smtpConfigured: Boolean,
        environment: Map<String, String> = System.getenv()
    ): Boolean = !gmailConfigured && !smtpConfigured && !environment["APP_ENV"].equals("production", ignoreCase = true)

    internal fun maskEmail(address: String): String {
        val at = address.indexOf('@')
        if (at <= 0) return "***"
        return address.first() + "***" + address.substring(at)
    }

    private val otpMinutes: Int get() = (plugins.AuthRules.OTP_TTL_MS / 60_000L).toInt()

    suspend fun sendVerificationOtp(to: String, otp: String): Boolean =
        sendEmail(EmailTemplates.verification(otp, otpMinutes), to)

    suspend fun sendEmailChangeOtp(to: String, otp: String): Boolean =
        sendEmail(EmailTemplates.emailChange(otp, to, otpMinutes), to)

    suspend fun sendPasswordResetOtp(to: String, otp: String): Boolean =
        sendEmail(EmailTemplates.passwordReset(otp, otpMinutes), to)

    suspend fun sendWorkspaceInvitation(to: String, workspaceName: String, inviterName: String, inviteCode: String): Boolean =
        sendEmail(EmailTemplates.workspaceInvitation(workspaceName, inviterName, inviteCode, validDays = 7), to)
}
