package com.collabsphere.app.dto.login

import kotlinx.serialization.Serializable

/** A password change revokes every existing session token, this one included, so [token] carries its replacement. */
@Serializable
data class UpdateProfileResponse(
    val success: Boolean = true,
    val token: String? = null
)
