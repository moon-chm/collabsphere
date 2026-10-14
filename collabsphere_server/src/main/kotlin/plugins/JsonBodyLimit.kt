package plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.ReceiveRequestBytes
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer

/** Small credential/OTP bodies on the unauthenticated auth routes. */
internal const val AUTH_JSON_BODY_LIMIT_BYTES = 16L * 1024

/** Authenticated JSON; the largest validated field (10k characters) is ~40KB even as 4-byte UTF-8. */
internal const val API_JSON_BODY_LIMIT_BYTES = 1L * 1024 * 1024

internal class JsonBodyLimitConfig {
    var maxBytes: Long = API_JSON_BODY_LIMIT_BYTES
}

private fun ApplicationCall.isJsonRequest(): Boolean =
    request.contentType().withoutParameters().match(ContentType.Application.Json)

/**
 * Caps JSON request bodies so a client cannot make the server buffer an arbitrarily large body
 * while deserializing it. A declared oversize body is refused with 413 before the handler runs;
 * a chunked one is cut off one byte past the cap while it is read. Multipart uploads (streamed to
 * disk with their own cap) and other content types are untouched.
 */
internal val JsonBodyLimit = createRouteScopedPlugin("JsonBodyLimit", ::JsonBodyLimitConfig) {
    val maxBytes = pluginConfig.maxBytes

    onCall { call ->
        if (!call.isJsonRequest()) return@onCall
        val declared = call.request.contentLength() ?: return@onCall
        if (declared > maxBytes) {
            call.respond(HttpStatusCode.PayloadTooLarge, "Request body too large")
        }
    }

    // Wraps the raw bytes, so the cap applies before any deserializer consumes the stream.
    on(ReceiveRequestBytes) { call, body ->
        if (!call.isJsonRequest()) return@on body
        call.writer {
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val read = body.readAvailable(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) throw PayloadTooLargeException(maxBytes)
                channel.writeFully(buffer, 0, read)
            }
        }.channel
    }
}

/** True when [JsonBodyLimit] cut the body off; deserializers rethrow the cap wrapped in their own exception. */
internal fun Throwable.causedByPayloadTooLarge(): Boolean =
    generateSequence(this) { it.cause }.any { it is PayloadTooLargeException }
