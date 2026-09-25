package com.collabsphere.app.view.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReactionComponentsTest {

    @Test
    fun typingLabelCoversEachGroupSize() {
        assertNull(typingLabel(emptyList()))
        assertEquals("Ana is typing…", typingLabel(listOf("Ana")))
        assertEquals("Ana and Ben are typing…", typingLabel(listOf("Ana", "Ben")))
        assertEquals("Several people are typing…", typingLabel(listOf("Ana", "Ben", "Cy")))
    }
}
