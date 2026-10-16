package com.collabsphere.app.remote.focus

import com.collabsphere.app.AppConfig
import com.collabsphere.app.dto.focus.FocusStatus
import com.collabsphere.app.dto.focus.HoldMessageRequest
import com.collabsphere.app.dto.focus.SetFocusRequest
import com.collabsphere.app.dto.message.ScheduledMessageItem
import com.collabsphere.app.remote.requireSuccess
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class FocusApiService(private val client: HttpClient) {

    suspend fun getMyFocus(): FocusStatus =
        client.get("${AppConfig.BASE_URL}/api/focus/me").requireSuccess().body()

    suspend fun setFocus(until: Long?): FocusStatus =
        client.put("${AppConfig.BASE_URL}/api/focus") {
            contentType(ContentType.Application.Json)
            setBody(SetFocusRequest(until))
        }.requireSuccess().body()

    suspend fun getFocus(userId: Int, workspaceId: Int): FocusStatus =
        client.get("${AppConfig.BASE_URL}/api/focus/$userId") {
            parameter("workspaceId", workspaceId)
        }.requireSuccess().body()

    suspend fun hold(request: HoldMessageRequest): ScheduledMessageItem =
        client.post("${AppConfig.BASE_URL}/api/focus/hold") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccess().body()
}
