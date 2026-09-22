package com.collabsphere.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val email: String,
    val password: String,
    val userName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val statusMessage: String? = null,
    val isEmailVerified: Boolean = false,
    val lastSeen: Long? = null
)
