package com.collabsphere.util

import java.net.InetAddress
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkPreviewServiceTest {

    @Test
    fun `private, loopback and link-local addresses are blocked`() {
        listOf("127.0.0.1", "10.1.2.3", "172.16.0.9", "192.168.1.1", "169.254.169.254", "100.64.0.1", "0.0.0.0", "::1", "fd00::1")
            .forEach { assertTrue(LinkPreviewService.isBlockedAddress(InetAddress.getByName(it)), it) }
    }

    @Test
    fun `public addresses are allowed`() {
        listOf("8.8.8.8", "1.1.1.1", "2606:4700:4700::1111")
            .forEach { assertFalse(LinkPreviewService.isBlockedAddress(InetAddress.getByName(it)), it) }
    }

    @Test
    fun `open graph tags win and relative images resolve against the page`() {
        val html = """
            <html><head>
            <title>Fallback title</title>
            <meta content="Launch &amp; Learn" property="og:title">
            <meta name="description" content="A short summary">
            <meta property="og:image" content="/img/cover.png">
            <meta property="og:site_name" content="Example">
            </head></html>
        """.trimIndent()
        val preview = LinkPreviewService.parse(html, URI("https://example.com/blog/post"))!!
        assertEquals("Launch & Learn", preview.title)
        assertEquals("A short summary", preview.description)
        assertEquals("https://example.com/img/cover.png", preview.imageUrl)
        assertEquals("Example", preview.siteName)
    }

    @Test
    fun `falls back to the title tag and host name`() {
        val preview = LinkPreviewService.parse("<title> Plain page </title>", URI("https://www.example.org/a"))!!
        assertEquals("Plain page", preview.title)
        assertEquals("example.org", preview.siteName)
        assertNull(preview.imageUrl)
    }

    @Test
    fun `pages without a title or description produce no preview`() {
        assertNull(LinkPreviewService.parse("<html><body>hi</body></html>", URI("https://example.com")))
    }
}
