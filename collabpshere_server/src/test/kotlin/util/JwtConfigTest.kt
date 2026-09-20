package com.collabsphere.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwtConfigTest {

    @Test
    fun `generateToken produces a token the verifier accepts with the correct claims`() {
        val token = JwtConfig.generateToken(userId = 123)
        val decoded = JwtConfig.verifier.verify(token)

        assertEquals(JwtConfig.issuer, decoded.issuer)
        assertTrue(decoded.audience.contains(JwtConfig.audience))
        assertEquals(123, decoded.getClaim("userId").asInt())
    }

    @Test
    fun `generateToken sets an expiry in the future`() {
        val token = JwtConfig.generateToken(userId = 1)
        val decoded = JwtConfig.verifier.verify(token)
        assertTrue(decoded.expiresAt.after(java.util.Date()))
    }

    @Test
    fun `verifier rejects a token signed with a different secret`() {
        val foreignToken = com.auth0.jwt.JWT.create()
            .withIssuer(JwtConfig.issuer)
            .withAudience(JwtConfig.audience)
            .withClaim("userId", 1)
            .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256("a-completely-different-secret"))

        assertTrue(
            runCatching { JwtConfig.verifier.verify(foreignToken) }.isFailure,
            "A token signed with the wrong secret must not verify"
        )
    }
}
