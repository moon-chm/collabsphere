package plugins

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.imageio.ImageIO

internal class UploadTooLargeException(limitBytes: Long) :
    Exception("File exceeds the ${limitBytes / (1024 * 1024)}MB upload limit")

/** Headroom for multipart boundaries and the small form fields sent alongside a file. */
private const val MULTIPART_OVERHEAD_BYTES = 64L * 1024

/**
 * True when the request *declares* a body bigger than [maxFileBytes] could ever need, so it can be
 * refused before a single byte is read. Chunked uploads declare no length and are caught while
 * streaming by [stageToTempFile] instead.
 */
internal fun ApplicationCall.declaredBodyExceeds(maxFileBytes: Long): Boolean {
    val declared = request.contentLength() ?: return false
    return declared > maxFileBytes + MULTIPART_OVERHEAD_BYTES
}

/**
 * Streams an uploaded file part to a temp file, holding only one small chunk in memory at a time,
 * and stops with [UploadTooLargeException] as soon as it passes [maxBytes] — whatever Content-Length
 * claimed. The caller owns the returned file and must delete it.
 */
internal suspend fun PartData.FileItem.stageToTempFile(maxBytes: Long): File = withContext(Dispatchers.IO) {
    val staged = File.createTempFile("upload-", ".part")
    try {
        streamProvider().use { input ->
            staged.outputStream().buffered().use { output ->
                val chunk = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = input.read(chunk)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) throw UploadTooLargeException(maxBytes)
                    output.write(chunk, 0, read)
                }
            }
        }
        staged
    } catch (e: Throwable) {
        staged.delete()
        throw e
    }
}

/** Reject non-images and unreasonable pixel dimensions before sending an avatar to storage. */
internal fun isValidAvatarImage(file: File): Boolean = runCatching {
    val imageInput = ImageIO.createImageInputStream(file) ?: return@runCatching false
    imageInput.use { input ->
        val readers = ImageIO.getImageReaders(input)
        if (!readers.hasNext()) return@runCatching false
        val reader = readers.next()
        try {
            reader.input = input
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            width > 0 && height > 0 && width <= 10_000 && height <= 10_000 && width.toLong() * height <= 50_000_000L
        } finally {
            reader.dispose()
        }
    }
}.getOrDefault(false)
