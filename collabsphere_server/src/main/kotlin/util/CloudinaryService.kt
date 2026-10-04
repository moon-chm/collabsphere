package com.collabsphere.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Lightweight, zero-external-dependency Cloudinary client using standard Java HttpURLConnection.
 * Eliminates issues with missing or incompatible Cloudinary SDK dependencies during Docker build.
 */
object CloudinaryService {

    private val cloudName: String by lazy {
        System.getenv("CLOUDINARY_CLOUD_NAME") ?: error("CLOUDINARY_CLOUD_NAME env var not set")
    }
    private val apiKey: String by lazy {
        System.getenv("CLOUDINARY_API_KEY") ?: error("CLOUDINARY_API_KEY env var not set")
    }
    private val apiSecret: String by lazy {
        System.getenv("CLOUDINARY_API_SECRET") ?: error("CLOUDINARY_API_SECRET env var not set")
    }

    private val jsonParser = Json { ignoreUnknownKeys = true }

    /**
     * Uploads an image file to Cloudinary using their REST API:
     * POST https://api.cloudinary.com/v1_1/<cloud_name>/image/upload
     */
    suspend fun uploadAvatar(file: File, publicId: String): String = withContext(Dispatchers.IO) {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val folder = "avatars"

        // Signature calculation: params sorted alphabetically (folder, overwrite, public_id, timestamp) + apiSecret
        val signature = sha1Hex("folder=$folder&overwrite=true&public_id=$publicId&timestamp=$timestamp$apiSecret")

        postMultipart(
            url = "https://api.cloudinary.com/v1_1/$cloudName/image/upload",
            fields = listOf(
                "api_key" to apiKey,
                "timestamp" to timestamp,
                "public_id" to publicId,
                "folder" to folder,
                "overwrite" to "true",
                "signature" to signature
            ),
            fileName = "$publicId.jpg",
            contentType = "image/jpeg",
            file = file,
            failureLabel = "Cloudinary upload"
        )
    }

    /**
     * Deletes an avatar by publicId from Cloudinary:
     * POST https://api.cloudinary.com/v1_1/<cloud_name>/image/destroy
     */
    suspend fun deleteAvatar(publicId: String): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val fullPublicId = "avatars/$publicId"
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val stringToSign = "public_id=$fullPublicId&timestamp=$timestamp$apiSecret"
            val signature = sha1Hex(stringToSign)

            val url = URL("https://api.cloudinary.com/v1_1/$cloudName/image/destroy")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }

            val formParams = "public_id=" + URLEncoder.encode(fullPublicId, "UTF-8") +
                    "&timestamp=" + URLEncoder.encode(timestamp, "UTF-8") +
                    "&api_key=" + URLEncoder.encode(apiKey, "UTF-8") +
                    "&signature=" + URLEncoder.encode(signature, "UTF-8")

            conn.outputStream.use { it.write(formParams.toByteArray(Charsets.UTF_8)) }
            conn.responseCode // execute request
        }
    }

    val isConfigured: Boolean
        get() = listOf("CLOUDINARY_CLOUD_NAME", "CLOUDINARY_API_KEY", "CLOUDINARY_API_SECRET")
            .all { !System.getenv(it).isNullOrBlank() }

    suspend fun uploadRawFile(file: File, folder: String, publicId: String, fileName: String, contentType: String): String =
        withContext(Dispatchers.IO) {
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val signature = sha1Hex("folder=$folder&public_id=$publicId&timestamp=$timestamp$apiSecret")
            postMultipart(
                url = "https://api.cloudinary.com/v1_1/$cloudName/raw/upload",
                fields = listOf(
                    "api_key" to apiKey,
                    "timestamp" to timestamp,
                    "public_id" to publicId,
                    "folder" to folder,
                    "signature" to signature
                ),
                fileName = fileName.replace("\"", ""),
                contentType = contentType,
                file = file,
                failureLabel = "Cloudinary raw upload"
            )
        }

    /**
     * POSTs [fields] plus [file] as multipart/form-data and returns the response's `secure_url`.
     * The body is streamed from disk with a fixed Content-Length — without it HttpURLConnection
     * buffers the entire request in memory to compute the length itself.
     */
    internal fun postMultipart(
        url: String,
        fields: List<Pair<String, String>>,
        fileName: String,
        contentType: String,
        file: File,
        failureLabel: String
    ): String {
        val boundary = "Boundary-" + System.currentTimeMillis()
        val crlf = "\r\n"
        val preamble = buildString {
            fields.forEach { (name, value) ->
                append("--$boundary${crlf}Content-Disposition: form-data; name=\"$name\"$crlf$crlf$value$crlf")
            }
            append("--$boundary${crlf}Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"${crlf}")
            append("Content-Type: $contentType$crlf$crlf")
        }.toByteArray(Charsets.UTF_8)
        val trailer = "$crlf--$boundary--$crlf".toByteArray(Charsets.UTF_8)

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            doInput = true
            useCaches = false
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setFixedLengthStreamingMode(preamble.size + file.length() + trailer.size)
        }
        try {
            conn.outputStream.use { os ->
                os.write(preamble)
                file.inputStream().use { it.copyTo(os) }
                os.write(trailer)
            }
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                return jsonParser.parseToJsonElement(responseBody).jsonObject["secure_url"]?.jsonPrimitive?.content
                    ?: error("Cloudinary response missing secure_url: $responseBody")
            }
            val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            error("$failureLabel failed (HTTP $responseCode): $errorBody")
        } finally {
            conn.disconnect()
        }
    }

    suspend fun deleteRawFile(secureUrl: String): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val fullPublicId = rawPublicIdFromUrl(secureUrl) ?: return@runCatching
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val signature = sha1Hex("public_id=$fullPublicId&timestamp=$timestamp$apiSecret")

            val conn = (URL("https://api.cloudinary.com/v1_1/$cloudName/raw/destroy").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            val formParams = "public_id=" + URLEncoder.encode(fullPublicId, "UTF-8") +
                    "&timestamp=" + URLEncoder.encode(timestamp, "UTF-8") +
                    "&api_key=" + URLEncoder.encode(apiKey, "UTF-8") +
                    "&signature=" + URLEncoder.encode(signature, "UTF-8")
            conn.outputStream.use { it.write(formParams.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
        }
    }

    fun isCloudinaryUrl(location: String): Boolean =
        location.startsWith("https://res.cloudinary.com/")

    internal fun rawPublicIdFromUrl(secureUrl: String): String? {
        val afterUpload = secureUrl.substringAfter("/raw/upload/", "")
        if (afterUpload.isEmpty()) return null
        val path = if (Regex("^v\\d+/").containsMatchIn(afterUpload)) afterUpload.substringAfter("/") else afterUpload
        return java.net.URLDecoder.decode(path, "UTF-8").ifBlank { null }
    }

    private fun sha1Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
