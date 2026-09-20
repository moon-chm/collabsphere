package com.example.rohit_project_challlange.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `matches falls back to direct comparison for a legacy plaintext-cached password`() {
        // Devices that cached a plaintext password before hashing was introduced must still be
        // able to authenticate offline via direct comparison, not fail every legacy login.
        assertTrue(PasswordHasher.matches("legacyPlain", "legacyPlain"))
        assertFalse(PasswordHasher.matches("wrong", "legacyPlain"))
    }

    @Test
    fun `matches does not throw on a malformed bcrypt-looking string`() {
        val malformed = "\$2a\$not-a-real-hash"
        assertTrue(PasswordHasher.isHashed(malformed))
        assertFalse(PasswordHasher.matches("password123", malformed))
    }
}
