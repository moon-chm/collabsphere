package com.collabsphere.util

/** Production file uploads must use durable storage; local disk remains available for development. */
internal object StoragePolicy {
    fun requiresDurableStorage(environment: Map<String, String> = System.getenv()): Boolean =
        environment["APP_ENV"].equals("production", ignoreCase = true)

    /**
     * Base for stored download links. A link is saved once and later fetched by every member's
     * device, so it must not echo the uploader's Host header: PUBLIC_BASE_URL wins, then Render's
     * automatic RENDER_EXTERNAL_URL. Only outside production may the request's own origin be used;
     * null means production has no configured base URL.
     */
    fun publicBaseUrl(
        requestScheme: String,
        requestHost: String,
        environment: Map<String, String> = System.getenv()
    ): String? {
        val configured = listOf("PUBLIC_BASE_URL", "RENDER_EXTERNAL_URL")
            .firstNotNullOfOrNull { key -> environment[key]?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } }
        if (configured != null) return configured
        if (requiresDurableStorage(environment)) return null
        return "$requestScheme://$requestHost"
    }
}
