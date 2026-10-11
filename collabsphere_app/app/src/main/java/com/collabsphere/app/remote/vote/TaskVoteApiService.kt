package com.collabsphere.app.remote.vote

import com.collabsphere.app.AppConfig
import com.collabsphere.app.dto.vote.CastVoteRequest
import com.collabsphere.app.dto.vote.TaskVoteProposalRequest
import com.collabsphere.app.dto.vote.TaskVoteProposalResponse
import com.collabsphere.app.dto.vote.TaskVoteProposalSyncResponse
import com.collabsphere.app.remote.requireSuccess
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class TaskVoteApiService(private val client: HttpClient) {

    private val baseUrl: String get() = "${AppConfig.BASE_URL}/api/task-votes"

    suspend fun submitProposal(request: TaskVoteProposalRequest): TaskVoteProposalResponse {
        return client.post("$baseUrl/proposals") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccess().body()
    }

    suspend fun castVote(proposalId: Int, vote: String): TaskVoteProposalResponse {
        return client.post("$baseUrl/$proposalId/vote") {
            contentType(ContentType.Application.Json)
            setBody(CastVoteRequest(vote = vote))
        }.requireSuccess().body()
    }

    suspend fun getProposalsByWorkspace(workspaceId: Int, status: String? = null): List<TaskVoteProposalResponse> {
        return client.get("$baseUrl/proposals/workspace/$workspaceId") {
            contentType(ContentType.Application.Json)
            if (!status.isNullOrBlank()) {
                parameter("status", status)
            }
        }.requireSuccess().body()
    }

    suspend fun syncProposals(workspaceId: Int, since: Long): List<TaskVoteProposalSyncResponse> {
        return client.get("$baseUrl/sync/$workspaceId") {
            contentType(ContentType.Application.Json)
            parameter("since", since)
        }.requireSuccess().body()
    }
}
