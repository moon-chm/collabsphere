package plugins

import kotlin.test.Test
import kotlin.test.assertTrue

class PublicPagesTest {

    @Test
    fun `privacy policy carries what Google's consent screen review looks for`() {
        val html = PublicPages.privacy()
        assertTrue(html.contains("Limited Use"))
        assertTrue(html.contains("Google API Services User Data Policy"))
        assertTrue(html.contains(PublicPages.CONTACT_EMAIL))
        assertTrue(html.contains("Delete account"))
        assertTrue(html.trimStart().startsWith("<!DOCTYPE html>"))
    }

    @Test
    fun `home page links to the privacy policy`() {
        val html = PublicPages.home()
        assertTrue(html.contains("href=\"/privacy\""))
        assertTrue(html.contains("CollabSphere"))
    }
}
