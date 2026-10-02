package com.collabsphere.app.remote.media

import com.collabsphere.app.AuthTokenHolder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MediaApiService {
    private val lenientJson = Json { ignoreUnknownKeys = true }

    /**
     * Uploads a file/image to the server's Cloudinary proxy endpoint.
     * Returns the secure Cloudinary URL on success.
     */
    suspend fun uploadMedia(baseUrl: String, fileBytes: ByteArray, mimeType: String, fileName: String): String {
        val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
        val boundary = "Boundary${System.currentTimeMillis()}"
        val token = AuthTokenHolder.token ?: throw IllegalStateException("Not authenticated")

        // Build raw multipart body manually (avoids extra ktor multipart plugin)
        val crlf = "\r\n"
        val headerPart = "--$boundary$crlf" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$crlf" +
            "Content-Type: $mimeType$crlf$crlf"
        val footer = "$crlf--$boundary--$crlf"
        val body = headerPart.toByteArray(Charsets.UTF_8) + fileBytes + footer.toByteArray(Charsets.UTF_8)

        // Use a plain HttpURLConnection so we don't need extra Ktor multipart support
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = java.net.URL("$cleanBaseUrl/api/media/upload")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            conn.outputStream.use { it.write(body) }
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                val element = lenientJson.parseToJsonElement(responseBody)
                element.jsonObject["url"]?.jsonPrimitive?.content
                    ?: error("Server response missing url field: $responseBody")
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                error("Media upload failed ($responseCode): $err")
            }
        }
    }
}
