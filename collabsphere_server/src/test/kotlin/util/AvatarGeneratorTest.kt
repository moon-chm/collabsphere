package com.collabsphere.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AvatarGeneratorTest {

    @Test
    fun `generate uses up to two initials from a multi-word name`() {
        val svg = AvatarGenerator.generate(1, "Rohit Kumbhar")
        assertTrue(svg.contains(">RK<"))
    }

    @Test
    fun `generate uses a single initial for a one-word name`() {
        val svg = AvatarGenerator.generate(1, "Rohit")
        assertTrue(svg.contains(">R<"))
    }

    @Test
    fun `generate falls back to a placeholder for a blank name`() {
        val svg = AvatarGenerator.generate(1, "   ")
        assertTrue(svg.contains(">?<"))
    }

    @Test
    fun `generate is deterministic for the same userId and username`() {
        val first = AvatarGenerator.generate(42, "Ada Lovelace")
        val second = AvatarGenerator.generate(42, "Ada Lovelace")
        assertEquals(first, second)
    }

    @Test
    fun `generate picks a background color deterministically by userId`() {
        val a = AvatarGenerator.generate(5, "Test User")
        val b = AvatarGenerator.generate(5, "Different Name")
        // Same userId (and thus the same palette index) must yield the same fill color,
        // regardless of the username used for the initials.
        val fillOf = { svg: String -> Regex("fill=\"(#[0-9A-Fa-f]{6})\"").find(svg)?.groupValues?.get(1) }
        assertEquals(fillOf(a), fillOf(b))
    }

    @Test
    fun `avatarUrlFor returns the stored url when present`() {
        assertEquals("/avatars/custom.jpg", AvatarGenerator.avatarUrlFor(7, "/avatars/custom.jpg"))
    }

    @Test
    fun `avatarUrlFor falls back to the default generated route when null or blank`() {
        assertEquals("/avatars/default/7", AvatarGenerator.avatarUrlFor(7, null))
        assertEquals("/avatars/default/7", AvatarGenerator.avatarUrlFor(7, ""))
    }
}
