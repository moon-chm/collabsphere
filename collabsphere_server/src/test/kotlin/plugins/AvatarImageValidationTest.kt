package plugins

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class AvatarImageValidationTest {
    @Test
    fun `rejects empty and malformed avatar files`() {
        val empty = File.createTempFile("avatar-empty", ".part")
        val malformed = File.createTempFile("avatar-malformed", ".part")
        try {
            malformed.writeBytes("not an image".toByteArray())
            assertFalse(isValidAvatarImage(empty))
            assertFalse(isValidAvatarImage(malformed))
        } finally {
            empty.delete()
            malformed.delete()
        }
    }

    @Test
    fun `accepts a decodable image within the pixel limit`() {
        val imageFile = File.createTempFile("avatar-valid", ".png")
        try {
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", imageFile)
            assertTrue(isValidAvatarImage(imageFile))
        } finally {
            imageFile.delete()
        }
    }
}
