package plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import org.slf4j.Logger

/**
 * A request the client can fix, reported as [status]. Thrown from inside a `dbQuery` block it also
 * rolls the transaction back.
 */
internal class RequestRejected(val status: HttpStatusCode, message: String) : Exception(message)

internal data class RouteFailure(val status: HttpStatusCode, val message: String)

/**
 * The status for a failure that escaped a route's own checks.
 *
 * The app's SyncPolicy drops queued offline work on a 4xx and retries it on a 5xx, so only failures
 * the client can fix may be reported as 4xx. Answering 400 for everything turned a brief database
 * outage into silently discarded channels, notes and messages; anything unrecognised here is a 500.
 */
internal fun classifyFailure(e: Throwable, logger: Logger, context: String): RouteFailure {
    if (e is kotlinx.coroutines.CancellationException) throw e
    return when {
        e is RequestRejected -> RouteFailure(e.status, e.message ?: "Request rejected")
        e.causedByPayloadTooLarge() -> RouteFailure(HttpStatusCode.PayloadTooLarge, "Request body too large")
        e is UnsupportedMediaTypeException -> RouteFailure(HttpStatusCode.UnsupportedMediaType, "Expected a JSON body")
        e is BadRequestException || e is ContentTransformationException ||
            e is kotlinx.serialization.SerializationException ->
            RouteFailure(HttpStatusCode.BadRequest, "Malformed request body")
        e.isUniqueViolation() -> RouteFailure(HttpStatusCode.Conflict, "Conflicts with an existing record")
        e.isForeignKeyViolation() -> RouteFailure(HttpStatusCode.BadRequest, "References a record that does not exist")
        else -> {
            logger.error("$context failed", e)
            RouteFailure(HttpStatusCode.InternalServerError, "$context failed")
        }
    }
}
