package com.collabsphere.app.model

import org.junit.Assert.assertTrue
import org.junit.Test

class TempIdTest {

    @Test
    fun `next always returns a negative id`() {
        repeat(1000) {
            assertTrue("TempId.next() must never collide with a real positive server id", TempId.next() < 0)
        }
    }

    @Test
    fun `nextLong always returns a negative id`() {
        repeat(1000) {
            assertTrue("TempId.nextLong() must never collide with a real positive server id", TempId.nextLong() < 0)
        }
    }
}
