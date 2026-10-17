package plugins

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.server.routing.routing
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `pages show the real logo and it is bundled`() {
        listOf(PublicPages.home(), PublicPages.privacy()).forEach { html ->
            assertTrue(html.contains("src=\"${PublicPages.LOGO_PATH}\""))
            assertTrue(html.contains("rel=\"icon\""))
        }
        val png = PublicPages.logoPng
        assertTrue(png != null && png.size > 8 && png[1] == 'P'.code.toByte() && png[2] == 'N'.code.toByte())
    }
    @Test
    fun `logo and pages are served over http`() = io.ktor.server.testing.testApplication {
        routing { publicPages() }
        val logo = client.get(PublicPages.LOGO_PATH)
        assertEquals(io.ktor.http.HttpStatusCode.OK, logo.status)
        assertEquals("image/png", logo.headers[io.ktor.http.HttpHeaders.ContentType])
        assertEquals("public, max-age=86400", logo.headers[io.ktor.http.HttpHeaders.CacheControl])
        assertTrue(logo.readRawBytes().size in 1..20_000)
        val home = client.get("/home")
        assertEquals(io.ktor.http.HttpStatusCode.OK, home.status)
        assertTrue(home.bodyAsText().contains(PublicPages.LOGO_PATH))
    }
}
