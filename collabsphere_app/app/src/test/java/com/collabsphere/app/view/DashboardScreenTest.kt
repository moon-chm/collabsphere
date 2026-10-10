package com.collabsphere.app.view

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class DashboardScreenTest {
    @Test
    fun `workspace accent index stays in range for negative and extreme ids`() {
        val paletteSize = 5
        listOf(Int.MIN_VALUE, -6, -5, -3, -1, 0, 1, 4, 5, Int.MAX_VALUE).forEach { id ->
            val index = workspaceAccentIndex(id)
            assertTrue("index $index for workspace id $id", index in 0 until paletteSize)
            assertEquals(Math.floorMod(id, paletteSize), index)
        }
    }
}
