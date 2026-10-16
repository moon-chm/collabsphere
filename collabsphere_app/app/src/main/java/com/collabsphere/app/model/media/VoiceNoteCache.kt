package com.collabsphere.app.model.media

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object VoiceNoteCache {
    private const val MAX_TOTAL_BYTES = 60L * 1024 * 1024
    private const val MAX_FILE_BYTES = 20L * 1024 * 1024
    private const val ALLOWED_PREFIX = "https://res.cloudinary.com/"

    @Volatile
    private var directory: File? = null
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun init(context: Context) {
        directory = File(context.cacheDir, "voice_notes").apply { mkdirs() }
    }

    fun clear() {
        directory?.listFiles()?.forEach { it.delete() }
    }

    fun keyFor(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(40)

    fun cachedFile(url: String): File? {
        val dir = directory ?: return null
        val file = File(dir, keyFor(url))
        return file.takeIf { it.isFile && it.length() > 0 }?.also { it.setLastModified(System.currentTimeMillis()) }
    }

    fun playbackSource(url: String): String = cachedFile(url)?.absolutePath ?: url

    suspend fun download(url: String) = withContext(Dispatchers.IO) {
        val dir = directory ?: return@withContext
        if (!url.startsWith(ALLOWED_PREFIX) || cachedFile(url) != null || !inFlight.add(url)) return@withContext
        val target = File(dir, keyFor(url))
        val partial = File(dir, "${target.name}.part")
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
            }
            try {
                if (connection.responseCode !in 200..299) return@withContext
                if (connection.contentLengthLong > MAX_FILE_BYTES) return@withContext
                var written = 0L
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            written += read
                            if (written > MAX_FILE_BYTES) return@withContext
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (written > 0 && partial.renameTo(target)) trim(dir)
            } finally {
                connection.disconnect()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            partial.delete()
            inFlight.remove(url)
        }
    }

    private fun trim(dir: File) {
        val files = dir.listFiles { f -> f.isFile && !f.name.endsWith(".part") }?.sortedByDescending { it.lastModified() } ?: return
        var total = 0L
        files.forEach { file ->
            total += file.length()
            if (total > MAX_TOTAL_BYTES) file.delete()
        }
    }
}
