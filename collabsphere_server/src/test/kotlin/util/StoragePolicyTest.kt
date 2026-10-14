package util

import com.collabsphere.util.StoragePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoragePolicyTest {
    @Test
    fun `local development permits local storage fallback`() {
        assertFalse(StoragePolicy.requiresDurableStorage(mapOf("APP_ENV" to "development")))
        assertFalse(StoragePolicy.requiresDurableStorage(emptyMap()))
    }

    @Test
    fun `production requires durable storage`() {
        assertTrue(StoragePolicy.requiresDurableStorage(mapOf("APP_ENV" to "production")))
        assertTrue(StoragePolicy.requiresDurableStorage(mapOf("APP_ENV" to "PRODUCTION")))
    }

    @Test
    fun `download links use the configured base url, never the request host, when one is set`() {
        val forged = "evil.example"
        assertEquals(
            "https://files.example.com",
            StoragePolicy.publicBaseUrl("https", forged, mapOf("PUBLIC_BASE_URL" to "https://files.example.com/"))
        )
        assertEquals(
            "https://app.onrender.com",
            StoragePolicy.publicBaseUrl("https", forged, mapOf("RENDER_EXTERNAL_URL" to "https://app.onrender.com", "APP_ENV" to "production"))
        )
        assertEquals(
            "https://files.example.com",
            StoragePolicy.publicBaseUrl(
                "https", forged,
                mapOf("PUBLIC_BASE_URL" to "https://files.example.com", "RENDER_EXTERNAL_URL" to "https://app.onrender.com")
            )
        )
    }

    @Test
    fun `production without a configured base url refuses rather than trusting the request host`() {
        assertNull(StoragePolicy.publicBaseUrl("https", "evil.example", mapOf("APP_ENV" to "production")))
        assertNull(StoragePolicy.publicBaseUrl("https", "evil.example", mapOf("APP_ENV" to "production", "PUBLIC_BASE_URL" to "  ")))
    }

    @Test
    fun `local development falls back to the request origin`() {
        assertEquals("http://10.0.2.2:8080", StoragePolicy.publicBaseUrl("http", "10.0.2.2:8080", emptyMap()))
    }
}
