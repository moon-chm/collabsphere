package com.example.rohit_project_challlange.dto.login

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val userId: Int,
    val userName: String,
    val currentPassword: String? = null,
    val newPassword: String? = null
)
