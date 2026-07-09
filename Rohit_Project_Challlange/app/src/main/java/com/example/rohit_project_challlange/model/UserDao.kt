package com.example.rohit_project_challlange.model

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

    @Query("SELECT COUNT(*) > 0 FROM users WHERE id = :userId")
    suspend fun userExists(userId: Int): Boolean

    @Query("UPDATE users SET userName = :newUserName WHERE id = :userId")
    suspend fun updateUsername(userId: Int, newUserName: String)

    @Query("UPDATE users SET userName = :newUserName, password = :newPassword WHERE id = :userId")
    suspend fun updateUserProfileWithPassword(userId: Int, newUserName: String, newPassword: String)
}