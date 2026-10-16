package com.collabsphere.app.model

import com.collabsphere.app.AppConfig
import com.collabsphere.app.dto.message.LinkPreview
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LinkPreviewRepo(
    private val client: HttpClient,
    private val diskCache: SwrCache? = null
) {

    private val cache = object : LinkedHashMap<String, LinkPreview?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LinkPreview?>?): Boolean = size > 100
    }
    private val mutex = Mutex()

    suspend fun preview(url: String): LinkPreview? {
        mutex.withLock { if (cache.containsKey(url)) return cache[url] }
        diskCache?.read(diskKey(url), LinkPreview.serializer())
            ?.takeIf { it.ageMs() < DISK_TTL_MS }
            ?.let { saved ->
                mutex.withLock { cache[url] = saved.value }
                return saved.value
            }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val response = client.get("${AppConfig.BASE_URL}/api/link-preview") { parameter("url", url) }
                if (response.status == HttpStatusCode.OK) response.body<LinkPreview>() else null
            }
        }
        if (result.isFailure) return null
        val preview = result.getOrNull()
        mutex.withLock { cache[url] = preview }
        if (preview != null) diskCache?.write(diskKey(url), LinkPreview.serializer(), preview)
        return preview
    }

    private fun diskKey(url: String) = "link_preview_$url"

    private companion object {
        const val DISK_TTL_MS = 24 * 60 * 60 * 1000L
    }
}
