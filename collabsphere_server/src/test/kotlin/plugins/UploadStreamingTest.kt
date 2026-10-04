package plugins

import com.collabsphere.util.CloudinaryService
import com.sun.net.httpserver.HttpServer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import kotlin.random.Random
import kotlin.test.*

class UploadStreamingTest {

    private fun filePart(bytes: ByteArray) = PartData.FileItem({ ByteReadChannel(bytes) }, {}, Headers.Empty)

    @Test
    fun `staging writes the exact bytes to a temp file`() = runBlocking {
        val bytes = Random(1).nextBytes(200_000)
        val staged = filePart(bytes).stageToTempFile(maxBytes = bytes.size.toLong())
        try {
            assertContentEquals(bytes, staged.readBytes())
        } finally {
            staged.delete()
        }
    }

    @Test
    fun `staging stops at the cap and leaves no temp file behind`() = runBlocking {
        val tmpDir = File(System.getProperty("java.io.tmpdir"))
        val before = tmpDir.listFiles { f -> f.name.startsWith("upload-") }?.toSet().orEmpty()
        assertFailsWith<UploadTooLargeException> {
            filePart(ByteArray(10_001)).stageToTempFile(maxBytes = 10_000)
        }
        val after = tmpDir.listFiles { f -> f.name.startsWith("upload-") }?.toSet().orEmpty()
        assertEquals(before, after)
    }

    @Test
    fun `cloudinary multipart body is streamed with an exact content length and intact file bytes`() {
        val fileBytes = Random(2).nextBytes(300_000)
        val file = File.createTempFile("cloudinary-test", ".bin").apply { writeBytes(fileBytes) }
        var receivedBody = ByteArray(0)
        var declaredLength: String? = null
        var transferEncoding: String? = null
        var contentType: String? = null

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/upload") { exchange ->
                declaredLength = exchange.requestHeaders.getFirst("Content-Length")
                transferEncoding = exchange.requestHeaders.getFirst("Transfer-Encoding")
                contentType = exchange.requestHeaders.getFirst("Content-Type")
                receivedBody = exchange.requestBody.readBytes()
                val response = """{"secure_url":"https://res.cloudinary.com/demo/raw/upload/v1/x.bin"}""".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }
        try {
            val url = CloudinaryService.postMultipart(
                url = "http://127.0.0.1:${server.address.port}/upload",
                fields = listOf("api_key" to "k", "signature" to "s"),
                fileName = "x.bin",
                contentType = "application/octet-stream",
                file = file,
                failureLabel = "test upload"
            )
            assertEquals("https://res.cloudinary.com/demo/raw/upload/v1/x.bin", url)
            assertNull(transferEncoding, "must not fall back to chunked encoding")
            assertEquals(receivedBody.size.toString(), declaredLength)

            val boundary = contentType!!.substringAfter("boundary=")
            val text = String(receivedBody, Charsets.ISO_8859_1) // 1:1 byte mapping, safe for binary
            assertTrue(text.contains("--$boundary\r\nContent-Disposition: form-data; name=\"api_key\"\r\n\r\nk\r\n"))
            val fileStart = text.indexOf("Content-Type: application/octet-stream\r\n\r\n") + "Content-Type: application/octet-stream\r\n\r\n".length
            val fileEnd = text.lastIndexOf("\r\n--$boundary--\r\n")
            assertEquals(receivedBody.size, fileEnd + "\r\n--$boundary--\r\n".length, "trailer must close the body")
            assertContentEquals(fileBytes, receivedBody.copyOfRange(fileStart, fileEnd))
        } finally {
            server.stop(0)
            file.delete()
        }
    }
}
