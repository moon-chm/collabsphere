package com.example.rohit_project_challlange.dto.login

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val id: Int,
    val userName: String,
    val email: String,
    val token: String? = null
)