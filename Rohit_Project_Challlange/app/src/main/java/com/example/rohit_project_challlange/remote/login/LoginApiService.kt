package com.example.rohit_project_challlange.remote.login

import com.example.rohit_project_challlange.dto.login.LoginRequest
import com.example.rohit_project_challlange.dto.login.RegisterRequest
import com.example.rohit_project_challlange.dto.login.LoginResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LoginApiService(private val client: HttpClient) {

    suspend fun login(request: LoginRequest): LoginResponse = withContext(Dispatchers.IO) {
        client.post("http://10.238.3.93:8080/api/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
            }
        }.body()
    }

    suspend fun register(request: RegisterRequest): LoginResponse = withContext(Dispatchers.IO) {
        client.post("http://10.238.3.93:8080/api/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
            }
        }.body()
    }
}