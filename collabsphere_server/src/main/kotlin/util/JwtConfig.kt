package com.collabsphere.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import java.util.concurrent.TimeUnit

object JwtConfig {
    private const val DEV_FALLBACK_SECRET = "dev-only-insecure-secret-change-me"
    private val secret = System.getenv("JWT_SECRET") ?: DEV_FALLBACK_SECRET

    const val issuer = "collabsphere-server"
    const val audience = "collabsphere-app"
    const val realm = "CollabSphere"

    private val algorithm = Algorithm.HMAC256(secret)
    private val validityMs = TimeUnit.DAYS.toMillis(30)

    val verifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun generateToken(userId: Int): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", userId)
        .withExpiresAt(Date(System.currentTimeMillis() + validityMs))
        .sign(algorithm)

    init {
        if (secret == DEV_FALLBACK_SECRET) {
            println("WARNING: JWT_SECRET env var is not set — using an insecure development fallback secret. Set JWT_SECRET before deploying to production.")
        }
    }
}
