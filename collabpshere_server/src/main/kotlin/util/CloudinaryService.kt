package com.collabsphere.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStream
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
     * Uploads raw image bytes to Cloudinary using their REST API:
     * POST https://api.cloudinary.com/v1_1/<cloud_name>/image/upload
     */
    suspend fun uploadAvatar(bytes: ByteArray, publicId: String): String = withContext(Dispatchers.IO) {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val folder = "avatars"

        // 1. Signature calculation: params sorted alphabetically (folder, overwrite, public_id, timestamp) + apiSecret
        val stringToSign = "folder=$folder&overwrite=true&public_id=$publicId&timestamp=$timestamp$apiSecret"
        val signature = sha1Hex(stringToSign)

        val boundary = "Boundary-" + System.currentTimeMillis()
        val url = URL("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            doInput = true
            useCaches = false
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        conn.outputStream.use { os ->
            // Write form text fields
            writeFormField(os, boundary, "api_key", apiKey)
            writeFormField(os, boundary, "timestamp", timestamp)
            writeFormField(os, boundary, "public_id", publicId)
            writeFormField(os, boundary, "folder", folder)
            writeFormField(os, boundary, "overwrite", "true")
            writeFormField(os, boundary, "signature", signature)

            // Write image file bytes
            writeFileField(os, boundary, "file", "$publicId.jpg", "image/jpeg", bytes)

            // Write boundary end
            os.write(("\r\n--$boundary--\r\n").toByteArray(Charsets.UTF_8))
            os.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode in 200..299) {
            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            val element = jsonParser.parseToJsonElement(responseBody)
            element.jsonObject["secure_url"]?.jsonPrimitive?.content
                ?: error("Cloudinary response missing secure_url: $responseBody")
        } else {
            val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            error("Cloudinary upload failed (HTTP $responseCode): $errorBody")
        }
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

    private fun sha1Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun writeFormField(os: OutputStream, boundary: String, name: String, value: String) {
        val part = "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n"
        os.write(part.toByteArray(Charsets.UTF_8))
    }

    private fun writeFileField(os: OutputStream, boundary: String, fieldName: String, fileName: String, contentType: String, bytes: ByteArray) {
        val header = "--$boundary\r\nContent-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\nContent-Type: $contentType\r\n\r\n"
        os.write(header.toByteArray(Charsets.UTF_8))
        os.write(bytes)
    }
}
