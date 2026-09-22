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
    val token: String? = null,
    val avatarUrl: String? = null,
    val isEmailVerified: Boolean = false
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val userName: String
)

@Serializable
data class UpdateProfileRequest(
    val userId: Int,
    val userName: String,
    val bio: String? = null,
    val statusMessage: String? = null,
    val currentPassword: String? = null,
    val newPassword: String? = null
)

@Serializable
data class RegisterResponse(
    val userId: Int,
    val email: String,
    val message: String
)

@Serializable
data class VerifyRegistrationRequest(
    val email: String,
    val otp: String
)

@Serializable
data class ResendVerificationRequest(
    val email: String
)

@Serializable
data class ForgotPasswordRequest(
    val email: String
)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val otp: String,
    val newPassword: String
)

@Serializable
data class AuthMessageResponse(
    val success: Boolean,
    val message: String
)
