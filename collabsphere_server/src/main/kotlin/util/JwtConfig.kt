package com.collabsphere.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

object JwtConfig {
    private val logger = LoggerFactory.getLogger(JwtConfig::class.java)
    private val secret = requireNotNull(System.getenv("JWT_SECRET")) {
        "JWT_SECRET environment variable must be configured"
    }

    const val issuer = "collabsphere-server"
    const val audience = "collabsphere-app"
    const val realm = "CollabSphere"

    private val algorithm = Algorithm.HMAC256(secret)
    private val validityMs = TimeUnit.DAYS.toMillis(30)

    val verifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    /** Claim carrying UsersTable.tokenVersion; tokens minted before this existed read as version 0. */
    const val TOKEN_VERSION_CLAIM = "tv"

    fun generateToken(userId: Int, tokenVersion: Int = 0): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", userId)
        .withClaim(TOKEN_VERSION_CLAIM, tokenVersion)
        .withExpiresAt(Date(System.currentTimeMillis() + validityMs))
        .sign(algorithm)

    private const val GITHUB_STATE_AUDIENCE = "collabsphere-github-state"
    private val githubStateValidityMs = TimeUnit.MINUTES.toMillis(10)

    private val githubStateVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(GITHUB_STATE_AUDIENCE)
        .build()

    fun generateGitHubState(userId: Int, workspaceId: Int): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(GITHUB_STATE_AUDIENCE)
        .withClaim("userId", userId)
        .withClaim("workspaceId", workspaceId)
        .withExpiresAt(Date(System.currentTimeMillis() + githubStateValidityMs))
        .sign(algorithm)

    fun verifyGitHubState(token: String): Pair<Int, Int>? = try {
        val decoded = githubStateVerifier.verify(token)
        val userId = decoded.getClaim("userId").asInt()
        val workspaceId = decoded.getClaim("workspaceId").asInt()
        if (userId != null && workspaceId != null) userId to workspaceId else null
    } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
        null
    }

}
