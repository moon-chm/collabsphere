package com.collabsphere.app.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registerUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Int): UserEntity?

    @Query("SELECT COUNT(*) > 0 FROM users WHERE id = :userId")
    suspend fun userExists(userId: Int): Boolean

    @Query("UPDATE users SET userName = :newUserName WHERE id = :userId")
    suspend fun updateUsername(userId: Int, newUserName: String)

    @Query("UPDATE users SET userName = :newUserName, password = :newPassword WHERE id = :userId")
    suspend fun updateUserProfileWithPassword(userId: Int, newUserName: String, newPassword: String)

    @Query("UPDATE users SET userName = :newUserName, bio = :bio, statusMessage = :statusMessage WHERE id = :userId")
    suspend fun updateProfileDetails(userId: Int, newUserName: String, bio: String?, statusMessage: String?)

    @Query("UPDATE users SET avatarUrl = :avatarUrl WHERE id = :userId")
    suspend fun updateAvatarUrl(userId: Int, avatarUrl: String?)

    @Query("UPDATE users SET email = :email, isEmailVerified = :isEmailVerified WHERE id = :userId")
    suspend fun updateEmail(userId: Int, email: String, isEmailVerified: Boolean)

    @Query(
        """
        UPDATE users SET
            userName = :userName,
            email = :email,
            avatarUrl = :avatarUrl,
            bio = :bio,
            statusMessage = :statusMessage,
            isEmailVerified = :isEmailVerified,
            lastSeen = :lastSeen
        WHERE id = :userId
        """
    )
    suspend fun cacheProfile(
        userId: Int,
        userName: String,
        email: String,
        avatarUrl: String?,
        bio: String?,
        statusMessage: String?,
        isEmailVerified: Boolean,
        lastSeen: Long?
    )

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Int)
}