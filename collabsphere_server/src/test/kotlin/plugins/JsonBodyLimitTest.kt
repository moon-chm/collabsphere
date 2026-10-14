package plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonBodyLimitTest {
    @Serializable
    private data class Payload(val text: String)

    private val handled = AtomicInteger()

    private fun ApplicationTestBuilder.limitedApp(limit: Long) {
        application {
            install(ContentNegotiation) { json() }
            // Same mapping as Application.module()'s StatusPages.
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    if (cause.causedByPayloadTooLarge()) call.respond(HttpStatusCode.PayloadTooLarge, "Request body too large")
                    else call.respond(HttpStatusCode.InternalServerError, "Internal server error")
                }
            }
            routing {
                route("/limited") {
                    install(JsonBodyLimit) { maxBytes = limit }
                    post {
                        handled.incrementAndGet()
                        val body = call.receive<Payload>()
                        call.respondText(body.text.length.toString())
                    }
                    post("/text") {
                        handled.incrementAndGet()
                        call.respondText(call.receiveText().length.toString())
                    }
                }
            }
        }
    }

    private fun json(textLength: Int) = """{"text":"${"a".repeat(textLength)}"}"""

    @Test
    fun `json within the limit is deserialized normally`() = testApplication {
        limitedApp(limit = 1024)
        val response = client.post("/limited") {
            contentType(ContentType.Application.Json.withCharset(Charsets.UTF_8))
            setBody(json(100))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("100", response.bodyAsText())
    }

    @Test
    fun `declared oversize json is refused before the handler runs`() = testApplication {
        limitedApp(limit = 1024)
        val response = client.post("/limited") {
            contentType(ContentType.Application.Json)
            setBody(json(5000))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals(0, handled.get())
    }

    @Test
    fun `chunked oversize json is cut off while it is read`() = testApplication {
        limitedApp(limit = 1024)
        val oversized = json(5000).toByteArray()
        val response = client.post("/limited") {
            // No declared length: forces the streaming check rather than the Content-Length one.
            setBody(object : OutgoingContent.WriteChannelContent() {
                override val contentType = ContentType.Application.Json
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    channel.writeFully(oversized)
                }
            })
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    @Test
    fun `non-json bodies are not limited`() = testApplication {
        limitedApp(limit = 1024)
        val response = client.post("/limited/text") {
            contentType(ContentType.Text.Plain)
            setBody("b".repeat(5000))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("5000", response.bodyAsText())
    }
}
