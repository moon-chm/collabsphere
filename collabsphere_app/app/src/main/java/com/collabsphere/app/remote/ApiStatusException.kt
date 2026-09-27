package com.collabsphere.app.remote

import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

/** The server answered, but with a non-2xx status. Network failures surface as IOExceptions instead. */
class ApiStatusException(val statusCode: Int) : Exception("Server responded with HTTP $statusCode")

/**
 * The shared HttpClient runs with `expectSuccess = false`, so a bare `.body()` on an error response
 * fails with a confusing deserialization error. Call this first to fail with the real status instead.
 */
fun HttpResponse.requireSuccess(): HttpResponse {
    if (!status.isSuccess()) throw ApiStatusException(status.value)
    return this
}
