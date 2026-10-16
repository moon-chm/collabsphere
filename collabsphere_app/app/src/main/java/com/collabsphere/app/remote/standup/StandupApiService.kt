package com.collabsphere.app.remote.standup

import com.collabsphere.app.AppConfig
import com.collabsphere.app.dto.standup.StandupDraft
import com.collabsphere.app.remote.requireSuccess
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class StandupApiService(private val client: HttpClient) {

    suspend fun draft(workspaceId: Int, since: Long, today: Long): StandupDraft =
        client.get("${AppConfig.BASE_URL}/api/standup/draft") {
            parameter("workspaceId", workspaceId)
            parameter("since", since)
            parameter("today", today)
        }.requireSuccess().body()
}
