package com.collabsphere.app.remote.progression

import com.collabsphere.app.AppConfig
import com.collabsphere.app.dto.progression.BadgeEvidenceResponse
import com.collabsphere.app.dto.progression.BadgeProgressResponse
import com.collabsphere.app.dto.progression.LedgerPageResponse
import com.collabsphere.app.dto.progression.ProgressionCatalogDto
import com.collabsphere.app.dto.progression.ProgressionSummaryResponse
import com.collabsphere.app.dto.progression.TimeZoneRequest
import com.collabsphere.app.remote.requireSuccess
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/** Read-only progression API. There is deliberately no call that sends SP, levels or badges. */
class ProgressionApiService(private val client: HttpClient) {

    private val baseUrl: String get() = "${AppConfig.BASE_URL}/api/progression"

    suspend fun getSummary(): ProgressionSummaryResponse =
        client.get("$baseUrl/summary").requireSuccess().body()

    /** Null when the cached catalog (identified by [etag]) is still current. */
    suspend fun getCatalog(etag: String?): Pair<ProgressionCatalogDto, String?>? {
        val response = client.get("$baseUrl/catalog") {
            if (etag != null) header(HttpHeaders.IfNoneMatch, etag)
        }
        if (response.status == HttpStatusCode.NotModified) return null
        return response.requireSuccess().body<ProgressionCatalogDto>() to response.headers[HttpHeaders.ETag]
    }

    suspend fun getBadges(): List<BadgeProgressResponse> =
        client.get("$baseUrl/badges").requireSuccess().body()

    suspend fun getEvidence(familyKey: String): List<BadgeEvidenceResponse> =
        client.get("$baseUrl/badges/$familyKey/evidence").requireSuccess().body()

    suspend fun getHistory(beforeId: Long?, limit: Int = 30): LedgerPageResponse =
        client.get("$baseUrl/history") {
            if (beforeId != null) parameter("beforeId", beforeId)
            parameter("limit", limit)
        }.requireSuccess().body()

    suspend fun updateTimeZone(zoneId: String) {
        client.put("${AppConfig.BASE_URL}/api/user/timezone") {
            contentType(ContentType.Application.Json)
            setBody(TimeZoneRequest(zoneId))
        }.requireSuccess()
    }
}
