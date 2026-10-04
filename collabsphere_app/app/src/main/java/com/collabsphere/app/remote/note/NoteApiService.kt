package com.collabsphere.app.remote.note

import com.collabsphere.app.remote.SyncPage
import com.collabsphere.app.remote.syncParameters
import com.collabsphere.app.remote.toSyncPage
import com.collabsphere.app.remote.requireSuccess
import com.collabsphere.app.AppConfig
import com.collabsphere.app.dto.notes.NotesRequest
import com.collabsphere.app.dto.notes.NotesResponse
import com.collabsphere.app.dto.notes.NotesSyncDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class NoteApiService(private val client: HttpClient) {

    private val baseUrl: String get() = "${AppConfig.BASE_URL}/api/notes"

    suspend fun createNotes(request: NotesRequest): NotesResponse {
        return client.post(baseUrl) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccess().body()
    }

    suspend fun updateNote(noteId: Int, request: NotesRequest): NotesResponse {
        return client.put("$baseUrl/$noteId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccess().body()
    }

    suspend fun setPinned(noteId: Int, pinned: Boolean) {
        val response = client.post("$baseUrl/$noteId/pin") {
            contentType(ContentType.Application.Json)
            setBody(com.collabsphere.app.dto.message.PinRequest(pinned))
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Pin failed (${response.status.value})")
        }
    }

    suspend fun getNotes(workspaceId: Int): List<NotesResponse> {
        return client.get("$baseUrl/workspace/$workspaceId").body()
    }

    suspend fun deleteNote(noteId: Int): HttpStatusCode =
        client.delete("$baseUrl/$noteId").status

    suspend fun getNoteUpdates(workspaceId: Int, since: Long, cursor: Long?): SyncPage<NotesSyncDto> {
        return client.get("$baseUrl/sync/$workspaceId") {
            syncParameters(since, cursor)
            contentType(ContentType.Application.Json)
        }.toSyncPage()
    }
}