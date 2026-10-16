package com.collabsphere.app.model.media

object CloudinaryImageUrls {

    private const val HOST_PREFIX = "https://res.cloudinary.com/"
    private const val MARKER = "/image/upload/"
    private val widthBuckets = intArrayOf(128, 256, 512, 1024, 2048)
    private val transformationSegment = Regex("^[a-z]{1,3}_[^/]*$")

    fun bucketFor(widthPx: Int?): Int {
        val wanted = widthPx?.takeIf { it > 0 } ?: 1024
        return widthBuckets.firstOrNull { it >= wanted } ?: widthBuckets.last()
    }

    fun sized(url: String, widthPx: Int?): String? {
        if (!url.startsWith(HOST_PREFIX)) return null
        val markerAt = url.indexOf(MARKER)
        if (markerAt < 0) return null
        val head = url.substring(0, markerAt + MARKER.length)
        val rest = url.substring(markerAt + MARKER.length)
        if (rest.isEmpty() || transformationSegment.matches(rest.substringBefore('/'))) return null
        val path = rest.substringBefore('?').lowercase()
        if (path.endsWith(".gif") || path.endsWith(".svg")) return null
        return "${head}c_limit,w_${bucketFor(widthPx)},q_auto,f_auto/$rest"
    }
}
