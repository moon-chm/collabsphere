package com.collabsphere.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class GitHubInstallationTokenResponse(
    val token: String,
    val expires_at: String
)

@Serializable
data class GitHubUserTokenResponse(
    val access_token: String,
    val expires_in: Long = 0,
    val refresh_token: String? = null,
    val refresh_token_expires_in: Long = 0
)

object GitHubAuthService {

    private val appId = System.getenv("GITHUB_APP_ID")
    private val clientId = System.getenv("GITHUB_CLIENT_ID")
    private val clientSecret = System.getenv("GITHUB_CLIENT_SECRET")
    private val privateKeyPem = System.getenv("GITHUB_PRIVATE_KEY_BASE64")

    private const val TOKEN_REFRESH_MARGIN_MS = 5 * 60 * 1000L

    private data class CachedToken(val token: String, val expiresAt: Long)

    private val installationTokens = ConcurrentHashMap<Long, CachedToken>()

    private val privateKey: RSAPrivateKey? by lazy {
        try {
            parsePrivateKey()
        } catch (e: Exception) {
            println("[GitHub] App private key unavailable, falling back to user tokens: ${e.message}")
            null
        }
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun parsePrivateKey(): RSAPrivateKey {
        if (privateKeyPem.isNullOrBlank()) {
            throw IllegalStateException("GITHUB_PRIVATE_KEY_BASE64 is not set")
        }

        val pem = if (privateKeyPem.contains("-----BEGIN")) {
            privateKeyPem
        } else {
            String(Base64.getMimeDecoder().decode(privateKeyPem))
        }
        val isPkcs1 = pem.contains("BEGIN RSA PRIVATE KEY")

        val body = pem
            .replace("\\n", "\n")
            .replace(Regex("-----(BEGIN|END) (RSA )?PRIVATE KEY-----"), "")
            .replace(Regex("\\s+"), "")

        val keyBytes = Base64.getDecoder().decode(body)
        val spec = PKCS8EncodedKeySpec(if (isPkcs1) pkcs1ToPkcs8(keyBytes) else keyBytes)
        return KeyFactory.getInstance("RSA").generatePrivate(spec) as RSAPrivateKey
    }

    private fun pkcs1ToPkcs8(pkcs1: ByteArray): ByteArray {
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val rsaAlgorithmId = byteArrayOf(
            0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(),
            0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
        )
        return derWrap(0x30, version + rsaAlgorithmId + derWrap(0x04, pkcs1))
    }

    private fun derWrap(tag: Int, content: ByteArray): ByteArray {
        val length = content.size
        val lengthBytes = when {
            length < 0x80 -> byteArrayOf(length.toByte())
            length < 0x100 -> byteArrayOf(0x81.toByte(), length.toByte())
            length < 0x10000 -> byteArrayOf(0x82.toByte(), (length shr 8).toByte(), length.toByte())
            else -> byteArrayOf(0x83.toByte(), (length shr 16).toByte(), (length shr 8).toByte(), length.toByte())
        }
        return byteArrayOf(tag.toByte()) + lengthBytes + content
    }

    private fun generateAppJwt(key: RSAPrivateKey): String {
        val now = Instant.now()
        return JWT.create()
            .withIssuedAt(Date.from(now.minusSeconds(60)))
            .withExpiresAt(Date.from(now.plusSeconds(9 * 60)))
            .withIssuer(appId)
            .sign(Algorithm.RSA256(null, key))
    }

    suspend fun getInstallationToken(installationId: Long): String? {
        val key = privateKey ?: return null
        if (appId.isNullOrBlank()) return null

        installationTokens[installationId]
            ?.takeIf { it.expiresAt - TOKEN_REFRESH_MARGIN_MS > System.currentTimeMillis() }
            ?.let { return it.token }

        try {
            val response = httpClient.post("https://api.github.com/app/installations/$installationId/access_tokens") {
                header(HttpHeaders.Authorization, "Bearer ${generateAppJwt(key)}")
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }
            if (!response.status.isSuccess()) {
                println("[GitHub] Failed to get installation token: ${response.status}")
                return null
            }
            val body = response.body<GitHubInstallationTokenResponse>()
            val expiresAt = parseGitHubTime(body.expires_at) ?: (System.currentTimeMillis() + 50 * 60 * 1000L)
            installationTokens[installationId] = CachedToken(body.token, expiresAt)
            return body.token
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun forgetInstallation(installationId: Long) {
        installationTokens.remove(installationId)
    }

    suspend fun revokeUserGrant(userToken: String) {
        if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) return
        try {
            val response = httpClient.delete("https://api.github.com/applications/$clientId/grant") {
                basicAuth(clientId, clientSecret)
                header(HttpHeaders.Accept, "application/vnd.github+json")
                contentType(ContentType.Application.Json)
                setBody(mapOf("access_token" to userToken))
            }
            if (!response.status.isSuccess() && response.status != HttpStatusCode.NotFound) {
                println("[GitHub] Revoking user grant failed: ${response.status}")
            }
        } catch (e: Exception) {
            println("[GitHub] Revoking user grant failed: ${e.message}")
        }
    }

    suspend fun exchangeCodeForUserToken(code: String): GitHubUserTokenResponse? {
        try {
            val response = httpClient.post("https://github.com/login/oauth/access_token") {
                header(HttpHeaders.Accept, "application/json")
                url {
                    parameters.append("client_id", clientId ?: "")
                    parameters.append("client_secret", clientSecret ?: "")
                    parameters.append("code", code)
                }
            }
            if (response.status.isSuccess()) {
                return response.body<GitHubUserTokenResponse>()
            }
            return null
        } catch (e: Exception) {
            println("[GitHub] Code exchange failed: ${e.message}")
            return null
        }
    }
}
