package com.collabsphere.util

import com.collabsphere.dto.LinkPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

object LinkPreviewService {

    private const val MAX_REDIRECTS = 3
    private const val MAX_BYTES = 256 * 1024
    private const val TIMEOUT_MS = 4_000
    private const val CACHE_SIZE = 300

    private val cache = object : LinkedHashMap<String, LinkPreview?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LinkPreview?>?): Boolean = size > CACHE_SIZE
    }

    suspend fun preview(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        synchronized(cache) {
            if (cache.containsKey(url)) return@withContext cache[url]
        }
        val result = runCatching { fetch(url) }.getOrNull()
        synchronized(cache) { cache[url] = result }
        result
    }

    internal fun isBlockedAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return true
        val bytes = address.address
        if (address is Inet4Address) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            if (first == 0 || first >= 224) return true
            if (first == 100 && second in 64..127) return true
            if (first == 169 && second == 254) return true
        }
        if (address is Inet6Address) {
            val first = bytes[0].toInt() and 0xFF
            if (first and 0xFE == 0xFC) return true
        }
        return false
    }

    private fun requirePublicHttpUrl(uri: URI): URI {
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") { "Unsupported scheme" }
        val host = requireNotNull(uri.host) { "Missing host" }
        require(uri.port == -1 || uri.port == 80 || uri.port == 443) { "Unsupported port" }
        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty() && addresses.none { isBlockedAddress(it) }) { "Blocked host" }
        return uri
    }

    private fun fetch(rawUrl: String): LinkPreview? {
        var current = requirePublicHttpUrl(URI(rawUrl.trim()))
        repeat(MAX_REDIRECTS + 1) {
            val connection = (current.toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "CollabSphereLinkPreview/1.0")
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return null
                    current = requirePublicHttpUrl(current.resolve(location))
                    return@repeat
                }
                if (code !in 200..299) return null
                val contentType = connection.contentType.orEmpty().lowercase()
                if (!contentType.contains("html")) return null
                val html = connection.inputStream.use { input ->
                    val buffer = ByteArray(MAX_BYTES)
                    var total = 0
                    while (total < MAX_BYTES) {
                        val read = input.read(buffer, total, MAX_BYTES - total)
                        if (read <= 0) break
                        total += read
                    }
                    String(buffer, 0, total, Charsets.UTF_8)
                }
                return parse(html, current)
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private val metaTag = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val attribute = Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")
    private val titleTag = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    internal fun parse(html: String, pageUri: URI): LinkPreview? {
        val metas = metaTag.findAll(html).map { tag ->
            attribute.findAll(tag.value).associate { match ->
                match.groupValues[1].lowercase() to (match.groups[3]?.value ?: match.groups[4]?.value.orEmpty())
            }
        }.toList()

        fun meta(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
            metas.firstOrNull { (it["property"] ?: it["name"])?.lowercase() == key }?.get("content")
        }?.let(::decodeEntities)?.trim()?.takeIf { it.isNotEmpty() }

        val title = meta("og:title", "twitter:title")
            ?: titleTag.find(html)?.groupValues?.get(1)?.let(::decodeEntities)?.trim()?.takeIf { it.isNotEmpty() }
        val description = meta("og:description", "twitter:description", "description")
        val image = meta("og:image", "twitter:image")?.let { raw ->
            runCatching { pageUri.resolve(raw) }.getOrNull()
                ?.takeIf { it.scheme == "http" || it.scheme == "https" }
                ?.toString()
        }
        val siteName = meta("og:site_name") ?: pageUri.host?.removePrefix("www.")

        if (title == null && description == null) return null
        return LinkPreview(
            url = pageUri.toString(),
            title = title?.take(200),
            description = description?.take(300),
            imageUrl = image,
            siteName = siteName
        )
    }

    private fun decodeEntities(text: String): String = text
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
}
