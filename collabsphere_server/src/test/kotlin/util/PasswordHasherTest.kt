package com.collabsphere.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {

    @Test
    fun `hash produces a bcrypt string recognized by isHashed`() {
        val hashed = PasswordHasher.hash("password123")
        assertTrue(PasswordHasher.isHashed(hashed))
        assertNotEquals("password123", hashed)
    }

    @Test
    fun `matches verifies the correct password against a bcrypt hash`() {
        val hashed = PasswordHasher.hash("password123")
        assertTrue(PasswordHasher.matches("password123", hashed))
        assertFalse(PasswordHasher.matches("wrongpassword", hashed))
    }

    @Test
    fun `isHashed is false for plaintext`() {
        assertFalse(PasswordHasher.isHashed("plainpassword"))
        assertFalse(PasswordHasher.isHashed(""))
    }

    @Test
    fun `matches falls back to direct comparison for legacy plaintext rows`() {
        // Rows written before hashing was introduced are stored as plaintext — matches() must
        // still authenticate them via direct comparison instead of failing every legacy login.
        assertTrue(PasswordHasher.matches("legacyPlain", "legacyPlain"))
        assertFalse(PasswordHasher.matches("wrong", "legacyPlain"))
    }

    @Test
    fun `matches does not throw on a malformed bcrypt-looking string`() {
        val malformed = "\$2a\$not-a-real-hash"
        assertTrue(PasswordHasher.isHashed(malformed))
        assertFalse(PasswordHasher.matches("password123", malformed))
    }

    @Test
    fun `async variants agree with the blocking ones and leave the calling thread`() = kotlinx.coroutines.runBlocking {
        val caller = Thread.currentThread()
        var hashingThread: Thread? = null
        val hashed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Unconfined) {
            PasswordHasher.hashAsync("password123").also { hashingThread = Thread.currentThread() }
        }
        assertTrue(PasswordHasher.matches("password123", hashed))
        assertTrue(PasswordHasher.matchesAsync("password123", hashed))
        assertFalse(PasswordHasher.matchesAsync("wrongpassword", hashed))
        assertNotEquals(caller, hashingThread, "bcrypt must run on its own pool, not the caller's thread")
    }
}
