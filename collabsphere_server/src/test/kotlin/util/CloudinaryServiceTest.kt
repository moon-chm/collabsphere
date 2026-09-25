package com.collabsphere.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudinaryServiceTest {

    @Test
    fun `rawPublicIdFromUrl strips the version segment`() {
        val url = "https://res.cloudinary.com/demo/raw/upload/v1712345678/workspace_files/7/abc_report.pdf"
        assertEquals("workspace_files/7/abc_report.pdf", CloudinaryService.rawPublicIdFromUrl(url))
    }

    @Test
    fun `rawPublicIdFromUrl works without a version segment`() {
        val url = "https://res.cloudinary.com/demo/raw/upload/workspace_files/7/abc_report.pdf"
        assertEquals("workspace_files/7/abc_report.pdf", CloudinaryService.rawPublicIdFromUrl(url))
    }

    @Test
    fun `rawPublicIdFromUrl returns null for a non raw url`() {
        assertNull(CloudinaryService.rawPublicIdFromUrl("https://res.cloudinary.com/demo/image/upload/v1/avatars/a.jpg"))
    }

    @Test
    fun `isCloudinaryUrl distinguishes cloud and disk locations`() {
        assertTrue(CloudinaryService.isCloudinaryUrl("https://res.cloudinary.com/demo/raw/upload/v1/x.pdf"))
        assertFalse(CloudinaryService.isCloudinaryUrl("/app/local_files_upload/x.pdf"))
    }
}
