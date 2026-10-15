package com.collabsphere.app.remote

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * The server answered, but with a non-2xx status. Network failures surface as IOExceptions instead.
 * [serverMessage] is the server's own short explanation ("Invalid or expired invite code"), when it sent one.
 */
class ApiStatusException(val statusCode: Int, val serverMessage: String? = null) :
    Exception(serverMessage ?: "Server responded with HTTP $statusCode")

/**
 * The shared HttpClient runs with `expectSuccess = false`, so a bare `.body()` on an error response
 * fails with a confusing deserialization error — and SyncPolicy reads that as a network failure and
 * retries forever. Call this first to fail with the real status instead.
 */
suspend fun HttpResponse.requireSuccess(): HttpResponse {
    if (!status.isSuccess()) throw ApiStatusException(status.value, readableErrorMessage(runCatching { bodyAsText() }.getOrNull()))
    return this
}

/** A plain-text error body worth showing to the user; JSON, booleans and stack-trace-sized bodies are not. */
internal fun readableErrorMessage(body: String?): String? {
    val text = body?.trim().orEmpty()
    if (text.isEmpty() || text.length > 200) return null
    if (text.startsWith("{") || text.startsWith("[") || text == "true" || text == "false") return null
    return text.removeSurrounding("\"")
}
