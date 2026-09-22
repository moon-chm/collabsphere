package com.collabsphere.app.dto.login

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val id: Int,
    val userName: String,
    val email: String,
    val token: String? = null,
    val avatarUrl: String? = null,
    val isEmailVerified: Boolean = false
)