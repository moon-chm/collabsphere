package plugins

import dto.SearchText
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTextTest {

    @Test
    fun `snippet keeps short text intact`() {
        assertEquals("Deploy on Friday", SearchText.snippet("Deploy on Friday", "friday"))
    }

    @Test
    fun `snippet trims long text around the match with ellipses`() {
        val text = "a".repeat(100) + "needle" + "b".repeat(100)
        val snippet = SearchText.snippet(text, "NEEDLE", radius = 5)
        assertEquals("…aaaaaneedlebbbbb…", snippet)
    }

    @Test
    fun `snippet falls back to the start when there is no match`() {
        assertEquals("abcd", SearchText.snippet("abcdefgh", "zz", radius = 2))
    }
}
