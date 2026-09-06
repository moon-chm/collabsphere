package com.example.rohit_project_challlange.remote.login

import com.example.rohit_project_challlange.AppConfig
import com.example.rohit_project_challlange.dto.login.LoginRequest
import com.example.rohit_project_challlange.dto.login.RegisterRequest
import com.example.rohit_project_challlange.dto.login.LoginResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LoginApiService(private val client: HttpClient) {

    suspend fun login(request: LoginRequest): LoginResponse = withContext(Dispatchers.IO) {
        val response: HttpResponse = client.post("${AppConfig.BASE_URL}/api/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
            }
        }

        if (response.status.isSuccess()) {
            response.body()
        } else {
            val errorBody = response.bodyAsText()
            throw Exception(errorBody.ifBlank { "Login failed (${response.status})" })
        }
    }

    suspend fun register(request: RegisterRequest): LoginResponse = withContext(Dispatchers.IO) {
        val response: HttpResponse = client.post("${AppConfig.BASE_URL}/api/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
            }
        }

        if (response.status.isSuccess()) {
            response.body()
        } else {
            val errorBody = response.bodyAsText()
            throw Exception(errorBody.ifBlank { "Registration failed (${response.status})" })
        }
    }

    suspend fun updateProfile(request: com.example.rohit_project_challlange.dto.login.UpdateProfileRequest): Boolean = withContext(Dispatchers.IO) {
        val response: HttpResponse = client.post("${AppConfig.BASE_URL}/api/user/profile") {
            contentType(ContentType.Application.Json)
            setBody(request)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
            }
        }
        response.status.isSuccess()
    }
}