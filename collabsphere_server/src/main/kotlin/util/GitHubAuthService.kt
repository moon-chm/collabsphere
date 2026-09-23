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

@Serializable
data class GitHubInstallationTokenResponse(
    val token: String,
    val expires_at: String
)

@Serializable
data class GitHubUserTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val refresh_token: String,
    val refresh_token_expires_in: Long
)

object GitHubAuthService {

    private val appId = System.getenv("GITHUB_APP_ID")
    private val clientId = System.getenv("GITHUB_CLIENT_ID")
    private val clientSecret = System.getenv("GITHUB_CLIENT_SECRET")
    // Note: Assuming private key is passed as a Base64 encoded string without header/footer 
    // or as a raw string. Let's handle a standard PEM format.
    private val privateKeyPem = System.getenv("GITHUB_PRIVATE_KEY_BASE64") 
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /**
     * Parses the PEM-formatted RSA Private Key.
     */
    private fun getPrivateKey(): RSAPrivateKey {
        if (privateKeyPem.isNullOrBlank()) {
            throw IllegalStateException("GITHUB_PRIVATE_KEY_BASE64 is not set")
        }
        
        // Remove standard PEM headers if present
        var privateKeyContent = privateKeyPem
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s+".toRegex(), "")
        
        val keyBytes = Base64.getDecoder().decode(privateKeyContent)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePrivate(spec) as RSAPrivateKey
    }

    /**
     * Generates a JWT to authenticate as the GitHub App.
     */
    fun generateAppJwt(): String {
        val now = Instant.now()
        val privateKey = getPrivateKey()
        val algorithm = Algorithm.RSA256(null, privateKey)
        
        return JWT.create()
            .withIssuedAt(Date.from(now.minusSeconds(60))) // 1 minute in the past for clock drift
            .withExpiresAt(Date.from(now.plusSeconds(10 * 60))) // Max 10 mins
            .withIssuer(appId)
            .sign(algorithm)
    }

    /**
     * Exchanges the GitHub installation ID for an Installation Access Token.
     */
    suspend fun getInstallationToken(installationId: Long): GitHubInstallationTokenResponse? {
        try {
            val jwt = generateAppJwt()
            val response = httpClient.post("https://api.github.com/app/installations/$installationId/access_tokens") {
                header(HttpHeaders.Authorization, "Bearer $jwt")
                header(HttpHeaders.Accept, "application/vnd.github.v3+json")
            }
            if (response.status.isSuccess()) {
                return response.body<GitHubInstallationTokenResponse>()
            }
            println("Failed to get installation token: ${response.status}")
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Exchanges a user's OAuth code for a User Access Token.
     */
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
            e.printStackTrace()
            return null
        }
    }
}
