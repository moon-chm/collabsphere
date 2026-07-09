package dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val id: Int,
    val userName: String,
    val email: String,
    val token: String? = null
)
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val userName: String
)