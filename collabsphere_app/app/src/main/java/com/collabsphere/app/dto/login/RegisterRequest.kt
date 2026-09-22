package com.collabsphere.app.dto.login

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val userName: String
)