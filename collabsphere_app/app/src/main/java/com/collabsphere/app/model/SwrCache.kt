package com.collabsphere.app.model

import android.content.Context
import com.collabsphere.app.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.security.MessageDigest

data class Cached<T>(val value: T, val savedAt: Long) {
    fun ageMs(now: Long = System.currentTimeMillis()): Long = (now - savedAt).coerceAtLeast(0)
}

class SwrCache(private val context: Context, private val userPreferences: UserPreferences) {

    @Serializable
    private data class Envelope(val savedAt: Long, val payload: JsonElement)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val writeLock = Mutex()

    suspend fun <T> read(key: String, serializer: KSerializer<T>): Cached<T>? = withContext(Dispatchers.IO) {
        val file = fileFor(key) ?: return@withContext null
        if (!file.isFile) return@withContext null
        runCatching {
            val envelope = json.decodeFromString(Envelope.serializer(), file.readText())
            Cached(json.decodeFromJsonElement(serializer, envelope.payload), envelope.savedAt)
        }.getOrElse {
            file.delete()
            null
        }
    }

    suspend fun <T> write(key: String, serializer: KSerializer<T>, value: T, savedAt: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            val file = fileFor(key) ?: return@withContext
            runCatching {
                val text = json.encodeToString(Envelope.serializer(), Envelope(savedAt, json.encodeToJsonElement(serializer, value)))
                writeLock.withLock {
                    val temp = File(file.parentFile, "${file.name}.tmp")
                    temp.writeText(text)
                    if (!temp.renameTo(file)) {
                        file.delete()
                        temp.renameTo(file)
                    }
                }
            }
        }

    suspend fun remove(key: String) = withContext(Dispatchers.IO) { fileFor(key)?.delete() }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        writeLock.withLock { root().deleteRecursively() }
    }

    private fun root() = File(context.cacheDir, "swr")

    private suspend fun fileFor(key: String): File? {
        val userId = userPreferences.userIdFlow.first()
        if (userId <= 0) return null
        val dir = File(root(), userId.toString()).apply { mkdirs() }
        return File(dir, "${fileName(key)}.json")
    }

    companion object {
        fun fileName(key: String): String {
            val safe = key.replace(Regex("[^A-Za-z0-9_-]"), "_")
            if (safe == key && key.length <= 80) return key
            val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
            return safe.take(40) + "_" + hash.take(16)
        }
    }
}
