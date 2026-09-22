package com.collabsphere.app.remote.login

import com.collabsphere.app.AppConfig
import com.collabsphere.app.dto.login.LoginRequest
import com.collabsphere.app.dto.login.RegisterRequest
import com.collabsphere.app.dto.login.LoginResponse
import com.collabsphere.app.dto.login.AvatarUploadResponse
import com.collabsphere.app.dto.login.ChangeEmailRequest
import com.collabsphere.app.dto.login.DeleteAccountRequest
import com.collabsphere.app.dto.login.EmailVerifyConfirmRequest
import com.collabsphere.app.dto.login.UserProfileResponse
import com.collabsphere.app.dto.login.SearchUserResult
import com.collabsphere.app.dto.login.PublicProfileResponse
import com.collabsphere.app.dto.login.BlockUserResponse
import com.collabsphere.app.dto.login.PrivacySettingsRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LoginApiService(private val client: HttpClient) {

    suspend fun login(request: LoginRequest): LoginResponse = withContext(Dispatchers.IO) {
        val response: HttpResponse = client.post("${AppConfig.BASE_URL}/api/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
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
        }

        if (response.status.isSuccess()) {
            response.body()
        } else {
            val errorBody = response.bodyAsText()
            throw Exception(errorBody.ifBlank { "Registration failed (${response.status})" })
        }
    }

    suspend fun updateProfile(request: com.collabsphere.app.dto.login.UpdateProfileRequest): Boolean = withContext(Dispatchers.IO) {
        val response: HttpResponse = client.post("${AppConfig.BASE_URL}/api/user/profile") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        response.status.isSuccess()
    }

    suspend fun getProfile(): UserProfileResponse = withContext(Dispatchers.IO) {
        val response: HttpResponse = client.get("${AppConfig.BASE_URL}/api/user/profile")
        if (response.status.isSuccess()) {
            response.body()
        } else {
            throw Exception(response.bodyAsText().ifBlank { "Failed to fetch profile (${response.status})" })
        }
    }

    suspend fun uploadAvatar(file: File): AvatarUploadResponse = withContext(Dispatchers.IO) {
        val response: HttpResponse = client.submitFormWithBinaryData(
            url = "${AppConfig.BASE_URL}/api/user/avatar",
            formData = formData {
                append(
                    "avatar",
                    InputProvider(file.length()) { file.inputStream().asInput() },
                    Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                    }
                )
            }
        )
        if (response.status.isSuccess()) {
            response.body()
        } else {
            throw Exception(response.bodyAsText().ifBlank { "Avatar upload failed (${response.status})" })
        }
    }

    suspend fun deleteAvatar(): AvatarUploadResponse = withContext(Dispatchers.IO) {
        val response: HttpResponse = client.delete("${AppConfig.BASE_URL}/api/user/avatar")
        if (response.status.isSuccess()) {
            response.body()
        } else {
            throw Exception(response.bodyAsText().ifBlank { "Failed to remove avatar (${response.status})" })
        }
    }

    suspend fun changeEmail(request: ChangeEmailRequest): HttpResponse = withContext(Dispatchers.IO) {
        client.put("${AppConfig.BASE_URL}/api/user/email") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun deleteAccount(request: DeleteAccountRequest): HttpResponse = withContext(Dispatchers.IO) {
        client.delete("${AppConfig.BASE_URL}/api/user/account") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun sendEmailVerification(): HttpResponse = withContext(Dispatchers.IO) {
        client.post("${AppConfig.BASE_URL}/api/user/verify-email/send")
    }

    suspend fun confirmEmailVerification(token: String): HttpResponse = withContext(Dispatchers.IO) {
        client.post("${AppConfig.BASE_URL}/api/user/verify-email/confirm") {
            contentType(ContentType.Application.Json)
            setBody(EmailVerifyConfirmRequest(token))
        }
    }

    suspend fun searchUsers(query: String): List<SearchUserResult> = withContext(Dispatchers.IO) {
        val response: HttpResponse = client.get("${AppConfig.BASE_URL}/api/user/search") {
            parameter("q", query)
        }
        if (response.status.isSuccess()) {
            response.body()
        } else {
            throw Exception(response.bodyAsText().ifBlank { "Search failed (${response.status})" })
        }
    }

    suspend fun getPublicProfile(userId: Int): PublicProfileResponse = withContext(Dispatchers.IO) {
        val response: HttpResponse = client.get("${AppConfig.BASE_URL}/api/user/$userId")
        if (response.status.isSuccess()) {
            response.body()
        } else {
            throw Exception(response.bodyAsText().ifBlank { "Failed to load profile (${response.status})" })
        }
    }

    suspend fun blockUser(targetUserId: Int): BlockUserResponse = withContext(Dispatchers.IO) {
        client.post("${AppConfig.BASE_URL}/api/user/block/$targetUserId").body()
    }

    suspend fun unblockUser(targetUserId: Int): BlockUserResponse = withContext(Dispatchers.IO) {
        client.delete("${AppConfig.BASE_URL}/api/user/block/$targetUserId").body()
    }

    suspend fun getBlockedUsers(): List<SearchUserResult> = withContext(Dispatchers.IO) {
        client.get("${AppConfig.BASE_URL}/api/user/blocks").body()
    }

    suspend fun updatePrivacySettings(request: PrivacySettingsRequest): HttpResponse = withContext(Dispatchers.IO) {
        client.put("${AppConfig.BASE_URL}/api/user/privacy") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}