package com.example.rohit_project_challlange.dto.login

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val userName: String
)