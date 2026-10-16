package com.collabsphere.app.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPipelineTest {

    private val photo = "https://res.cloudinary.com/demo/image/upload/v1712345678/collabsphere/abc.jpg"

    @Test
    fun cloudinaryPhotosAreResizedToABucket() {
        assertEquals(
            "https://res.cloudinary.com/demo/image/upload/c_limit,w_1024,q_auto,f_auto/v1712345678/collabsphere/abc.jpg",
            CloudinaryImageUrls.sized(photo, 720)
        )
        assertTrue(CloudinaryImageUrls.sized(photo, 90)!!.contains("w_128"))
        assertTrue(CloudinaryImageUrls.sized(photo, null)!!.contains("w_1024"))
        assertTrue(CloudinaryImageUrls.sized(photo, 5000)!!.contains("w_2048"))
    }

    @Test
    fun nearbySizesShareOneCachedVariant() {
        assertEquals(CloudinaryImageUrls.sized(photo, 300), CloudinaryImageUrls.sized(photo, 500))
        assertNotEquals(CloudinaryImageUrls.sized(photo, 500), CloudinaryImageUrls.sized(photo, 600))
    }

    @Test
    fun leavesEverythingElseUntouched() {
        assertNull(CloudinaryImageUrls.sized("https://collabsphere-server-qtke.onrender.com/avatars/default/7", 128))
        assertNull(CloudinaryImageUrls.sized("https://res.cloudinary.com/demo/video/upload/voice/a.m4a", 128))
        assertNull(CloudinaryImageUrls.sized("https://res.cloudinary.com/demo/image/upload/c_fill,w_200/v1/a.jpg", 128))
        assertNull(CloudinaryImageUrls.sized("https://res.cloudinary.com/demo/image/upload/v1/party.gif", 128))
        assertNull(CloudinaryImageUrls.sized("https://res.cloudinary.com/demo/image/upload/v1/logo.SVG", 128))
        assertNull(CloudinaryImageUrls.sized("http://res.cloudinary.com/demo/image/upload/v1/a.jpg", 128))
        assertNull(CloudinaryImageUrls.sized("https://evil.example/res.cloudinary.com/demo/image/upload/v1/a.jpg", 128))
    }

    @Test
    fun compressionMathKeepsAspectRatioAndCapsTheLongEdge() {
        assertEquals(1600 to 1200, ImageCompressor.scaledSize(4000, 3000))
        assertEquals(900 to 1600, ImageCompressor.scaledSize(2700, 4800))
        assertEquals(800 to 600, ImageCompressor.scaledSize(800, 600))
        assertEquals(2, ImageCompressor.sampleSizeFor(4000, 3000))
        assertEquals(1, ImageCompressor.sampleSizeFor(3000, 2000))
        assertEquals(4, ImageCompressor.sampleSizeFor(8000, 6000))
    }

    @Test
    fun onlyStillImagesAreCompressed() {
        assertTrue(ImageCompressor.isCompressible("image/jpeg"))
        assertTrue(ImageCompressor.isCompressible("IMAGE/PNG"))
        assertTrue(ImageCompressor.isCompressible("image/heic"))
        assertFalse(ImageCompressor.isCompressible("image/gif"))
        assertFalse(ImageCompressor.isCompressible("application/pdf"))
        assertFalse(ImageCompressor.isCompressible(null))
    }

    @Test
    fun voiceCacheKeysAreStableAndFilenameSafe() {
        val key = VoiceNoteCache.keyFor("https://res.cloudinary.com/demo/video/upload/voice/a.m4a")
        assertEquals(key, VoiceNoteCache.keyFor("https://res.cloudinary.com/demo/video/upload/voice/a.m4a"))
        assertEquals(40, key.length)
        assertTrue(key.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
