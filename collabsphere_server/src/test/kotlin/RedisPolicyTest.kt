package com.collabsphere

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedisPolicyTest {
    @Test
    fun `redis stays optional for the existing single-instance deployment`() {
        assertFalse(redisRequiredByConfiguration(null, null))
        assertFalse(redisRequiredByConfiguration("false", "1"))
    }

    @Test
    fun `redis is mandatory when multi-instance mode is requested`() {
        assertTrue(redisRequiredByConfiguration("true", "1"))
        assertTrue(redisRequiredByConfiguration(null, "2"))
    }
}
