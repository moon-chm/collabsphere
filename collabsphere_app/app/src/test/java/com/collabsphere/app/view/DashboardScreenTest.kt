package com.collabsphere.app.view

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DashboardScreenTest {
    @Test
    fun `workspace accent index stays in range for negative and extreme ids`() {
        val paletteSize = 5
        listOf(Int.MIN_VALUE, -6, -5, -3, -1, 0, 1, 4, 5, Int.MAX_VALUE).forEach { id ->
            val index = workspaceAccentIndex(id)
            assertTrue(index in 0 until paletteSize, "index $index for workspace id $id")
            assertEquals(Math.floorMod(id, paletteSize), index)
        }
    }
}
