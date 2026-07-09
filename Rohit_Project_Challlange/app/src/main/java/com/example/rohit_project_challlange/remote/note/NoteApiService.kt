package com.example.rohit_project_challlange.remote.note

import com.example.rohit_project_challlange.dto.notes.NotesRequest
import com.example.rohit_project_challlange.dto.notes.NotesResponse
import com.example.rohit_project_challlange.dto.notes.NotesSyncDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.http.*

class NoteApiService(private val client: HttpClient) {

    private val baseUrl = "http://10.238.3.93:8080/api/notes"

    suspend fun createNotes(request: NotesRequest): NotesResponse {
        return client.post(baseUrl) {
            contentType(ContentType.Application.Json)
            setBody(request)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }.body()
    }

    suspend fun updateNote(noteId: Int, request: NotesRequest): NotesResponse {
        return client.put("$baseUrl/$noteId") {
            contentType(ContentType.Application.Json)
            setBody(request)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }.body()
    }

    suspend fun getNotes(workspaceId: Int): List<NotesResponse> {
        return client.get("$baseUrl/workspace/$workspaceId") {
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }.body()
    }

    suspend fun deleteNote(noteId: Int): Boolean {
        val response = client.delete("$baseUrl/$noteId") {
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }
        return response.status.isSuccess()
    }

    suspend fun getNoteUpdates(workspaceId: Int, since: Long): List<NotesSyncDto> {
        return client.get("$baseUrl/sync/$workspaceId") {
            parameter("since", since)
            contentType(ContentType.Application.Json)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }.body()
    }
}