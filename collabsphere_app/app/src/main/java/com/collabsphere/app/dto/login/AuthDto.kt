package com.collabsphere.app.dto.login

import kotlinx.serialization.Serializable

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
    val success: Boolean = true,
    val message: String
)
