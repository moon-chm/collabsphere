package plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import org.slf4j.helpers.NOPLogger
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The app's SyncPolicy drops queued work on a 4xx and retries it on a 5xx, so these statuses decide
 * whether a failed offline change is kept or thrown away.
 */
class RouteFailuresTest {

    private fun statusOf(e: Throwable) = classifyFailure(e, NOPLogger.NOP_LOGGER, "Test").status

    @Test
    fun `database outages are retryable server errors`() {
        val connectionLost = SQLException("Connection refused", "08001")
        assertEquals(HttpStatusCode.InternalServerError, statusOf(connectionLost))
        assertEquals(HttpStatusCode.InternalServerError, statusOf(IllegalStateException("pool exhausted", connectionLost)))
        assertEquals(HttpStatusCode.InternalServerError, statusOf(RuntimeException("unexpected")))
    }

    @Test
    fun `malformed bodies are client errors`() {
        assertEquals(HttpStatusCode.BadRequest, statusOf(BadRequestException("bad json")))
        assertEquals(HttpStatusCode.BadRequest, statusOf(SerializationException("missing field")))
        assertEquals(HttpStatusCode.UnsupportedMediaType, statusOf(UnsupportedMediaTypeException(ContentType.Text.Plain)))
        assertEquals(HttpStatusCode.PayloadTooLarge, statusOf(BadRequestException("wrapped", PayloadTooLargeException(10))))
    }

    @Test
    fun `explicit rejections keep their status and message`() {
        val failure = classifyFailure(RequestRejected(HttpStatusCode.Conflict, "taken"), NOPLogger.NOP_LOGGER, "Test")
        assertEquals(HttpStatusCode.Conflict, failure.status)
        assertEquals("taken", failure.message)
    }

    @Test
    fun `constraint violations are client errors`() {
        assertEquals(HttpStatusCode.Conflict, statusOf(RuntimeException(SQLException("duplicate key", "23505"))))
        assertEquals(HttpStatusCode.BadRequest, statusOf(RuntimeException(SQLException("missing parent", "23503"))))
    }

    @Test
    fun `cancellation is never swallowed`() {
        assertFailsWith<CancellationException> { statusOf(CancellationException("client went away")) }
    }
}
