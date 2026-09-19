package com.example.rohit_project_challlange.model
import android.util.Log

import com.example.rohit_project_challlange.UserPreferences
import com.example.rohit_project_challlange.dto.login.LoginRequest
import com.example.rohit_project_challlange.dto.login.RegisterRequest
import com.example.rohit_project_challlange.dto.login.LoginResponse
import com.example.rohit_project_challlange.remote.login.LoginApiService

class UserRepo(
    private val userDao: UserDao,
    private val apiService: LoginApiService,
    private val userPreferences: UserPreferences
) {

    suspend fun registerRemote(email: String, username: String, pass: String): Result<LoginResponse> {
        return try {
            val apiResponse: LoginResponse = apiService.register(RegisterRequest(email, pass, username))
            userPreferences.saveAuthToken(apiResponse.token)

            val localUser = UserEntity(
                id = apiResponse.id,
                email = email,
                userName = username,
                password = PasswordHasher.hash(pass)
            )
            userDao.registerUser(localUser)

            Result.success(apiResponse)
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun loginRemote(email: String, pass: String): Result<UserEntity> {
        val localUser = userDao.getUserByEmail(email)
        return try {
            val responseDto = apiService.login(LoginRequest(email, pass))
            userPreferences.saveAuthToken(responseDto.token)
            val userEntity = UserEntity(
                id = responseDto.id,
                email = responseDto.email,
                userName = responseDto.userName,
                password = PasswordHasher.hash(pass)
            )
            userDao.registerUser(userEntity)
            Result.success(userEntity)
        } catch (e: Exception) {
            if (localUser != null) {
                if (PasswordHasher.matches(pass, localUser.password)) {
                    if (!PasswordHasher.isHashed(localUser.password)) {
                        userDao.registerUser(localUser.copy(password = PasswordHasher.hash(pass)))
                    }
                    Result.success(localUser)
                } else {
                    Result.failure(Exception("Invalid password"))
                }
            } else {
                Result.failure(Exception(e.message ?: "Authentication failed"))
            }
        }
    }

    suspend fun getUserById(userId: Int): UserEntity? {
        return userDao.getUserById(userId)
    }

    suspend fun updateProfile(
        userId: Int,
        userEmail: String,
        newName: String,
        currentPassword: String?,
        newPassword: String?
    ): Boolean {
        return try {
            val user = userDao.getUserByEmail(userEmail) ?: userDao.getUserById(userId)
            if (user == null || user.id != userId) return false

            if (!newPassword.isNullOrBlank() && !currentPassword.isNullOrBlank()) {
                val currentMatches = user.password.isEmpty() || PasswordHasher.matches(currentPassword, user.password)
                if (!currentMatches) return false
            }

            val remoteSuccess = try {
                apiService.updateProfile(
                    com.example.rohit_project_challlange.dto.login.UpdateProfileRequest(
                        userId = userId,
                        userName = newName,
                        currentPassword = currentPassword,
                        newPassword = newPassword
                    )
                )
            } catch (e: Exception) {
                // If offline / network unreachable, allow local cache update
                true
            }

            if (!remoteSuccess) {
                return false
            }

            if (!newPassword.isNullOrBlank() && !currentPassword.isNullOrBlank()) {
                userDao.updateUserProfileWithPassword(userId, newName, PasswordHasher.hash(newPassword))
            } else {
                userDao.updateUsername(userId, newName)
            }

            true
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            false
        }
    }
}