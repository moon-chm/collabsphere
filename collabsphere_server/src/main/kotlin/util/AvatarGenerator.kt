package com.collabsphere.util

object AvatarGenerator {

    // 12 curated, vibrant background colours (hex). Picked by userId % 12 — deterministic & unique per user.
    private val PALETTE = listOf(
        "#5C6BC0", // indigo
        "#26A69A", // teal
        "#EF5350", // red
        "#AB47BC", // purple
        "#FF7043", // deep orange
        "#29B6F6", // light blue
        "#66BB6A", // green
        "#FFA726", // orange
        "#EC407A", // pink
        "#8D6E63", // brown
        "#78909C", // blue-grey
        "#26C6DA"  // cyan
    )

    /**
     * Generates an inline SVG avatar with the user's initials (up to 2 chars)
     * on a deterministic coloured background.
     */
    fun generate(userId: Int, username: String): String {
        val initials = username
            .trim()
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifEmpty { "?" }

        val bg = PALETTE[Math.abs(userId) % PALETTE.size]

        return """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 200 200">
  <circle cx="100" cy="100" r="100" fill="$bg"/>
  <text x="100" y="100"
        font-family="'Inter','Segoe UI',Arial,sans-serif"
        font-size="80"
        font-weight="600"
        fill="#FFFFFF"
        text-anchor="middle"
        dominant-baseline="central">$initials</text>
</svg>"""
    }

    /**
     * Returns the avatar URL to include in any profile response.
     * If the user has an uploaded avatar, returns that URL.
     * Otherwise returns the dynamic SVG route so the client always gets a valid image URL.
     */
    fun avatarUrlFor(userId: Int, storedAvatarUrl: String?): String =
        if (!storedAvatarUrl.isNullOrBlank()) storedAvatarUrl
        else "/avatars/default/$userId"
}
