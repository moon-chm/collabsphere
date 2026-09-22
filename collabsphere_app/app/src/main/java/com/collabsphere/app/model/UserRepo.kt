package com.collabsphere.app.model
import android.util.Log

import com.collabsphere.app.UserPreferences
import com.collabsphere.app.dto.login.LoginRequest
import com.collabsphere.app.dto.login.RegisterRequest
import com.collabsphere.app.dto.login.RegisterResponse
import com.collabsphere.app.dto.login.LoginResponse
import com.collabsphere.app.dto.login.ChangeEmailRequest
import com.collabsphere.app.dto.login.DeleteAccountRequest
import com.collabsphere.app.dto.login.UserProfileResponse
import com.collabsphere.app.dto.login.SearchUserResult
import com.collabsphere.app.dto.login.PublicProfileResponse
import com.collabsphere.app.dto.login.PrivacySettingsRequest
import com.collabsphere.app.remote.login.LoginApiService
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.io.File

class UserRepo(
    private val userDao: UserDao,
    private val apiService: LoginApiService,
    private val userPreferences: UserPreferences
) {

    suspend fun registerRemote(email: String, username: String, pass: String): Result<RegisterResponse> {
        return try {
            val apiResponse: RegisterResponse = apiService.register(RegisterRequest(email, pass, username))
            Result.success(apiResponse)
        } catch (e: Exception) {
            Log.e("UserRepo", "Registration failed", e)
            Result.failure(e)
        }
    }

    suspend fun verifyRegistration(email: String, otp: String): Result<String> {
        return try {
            val res = apiService.verifyRegistration(email, otp)
            Result.success(res.message)
        } catch (e: Exception) {
            Log.e("UserRepo", "Verification failed", e)
            Result.failure(e)
        }
    }

    suspend fun resendVerification(email: String): Result<String> {
        return try {
            val res = apiService.resendVerification(email)
            Result.success(res.message)
        } catch (e: Exception) {
            Log.e("UserRepo", "Resend verification failed", e)
            Result.failure(e)
        }
    }

    suspend fun forgotPassword(email: String): Result<String> {
        return try {
            val res = apiService.forgotPassword(email)
            Result.success(res.message)
        } catch (e: Exception) {
            Log.e("UserRepo", "Forgot password failed", e)
            Result.failure(e)
        }
    }

    suspend fun resetPassword(email: String, otp: String, newPass: String): Result<String> {
        return try {
            val res = apiService.resetPassword(email, otp, newPass)
            Result.success(res.message)
        } catch (e: Exception) {
            Log.e("UserRepo", "Reset password failed", e)
            Result.failure(e)
        }
    }

    suspend fun loginRemote(email: String, pass: String): Result<UserEntity> {
        val localUser = userDao.getUserByEmail(email)
        return try {
            val responseDto = apiService.login(LoginRequest(email, pass))
            userPreferences.saveAuthToken(responseDto.token)
            // Re-hashing costs a deliberate ~250ms (bcrypt) — skip it when the cached local hash
            // already matches this exact password instead of paying that cost on every login.
            val passwordHash = if (localUser != null && PasswordHasher.isHashed(localUser.password) &&
                PasswordHasher.matches(pass, localUser.password)
            ) {
                localUser.password
            } else {
                PasswordHasher.hash(pass)
            }
            val userEntity = UserEntity(
                id = responseDto.id,
                email = responseDto.email,
                userName = responseDto.userName,
                password = passwordHash,
                avatarUrl = responseDto.avatarUrl,
                isEmailVerified = responseDto.isEmailVerified
            )
            userDao.registerUser(userEntity)
            Result.success(userEntity)
        } catch (e: Exception) {
            val errorMsg = e.message ?: ""
            if (errorMsg.contains("EMAIL_NOT_VERIFIED", ignoreCase = true) ||
                errorMsg.contains("verify your email", ignoreCase = true) ||
                errorMsg.contains("403", ignoreCase = true)
            ) {
                return Result.failure(Exception("EMAIL_NOT_VERIFIED: Please verify your email before logging in."))
            }

            if (localUser != null) {
                if (!localUser.isEmailVerified) {
                    return Result.failure(Exception("EMAIL_NOT_VERIFIED: Please verify your email before logging in."))
                }
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
        bio: String? = null,
        statusMessage: String? = null,
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
                    com.collabsphere.app.dto.login.UpdateProfileRequest(
                        userId = userId,
                        userName = newName,
                        bio = bio,
                        statusMessage = statusMessage,
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
            userDao.updateProfileDetails(userId, newName, bio, statusMessage)

            true
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            false
        }
    }

    /** Fetches the full profile from the server and caches it; falls back to the local cache when offline. */
    suspend fun fetchProfile(userId: Int): Result<UserProfileResponse> {
        return try {
            val profile = apiService.getProfile()
            userDao.cacheProfile(
                userId = profile.id,
                userName = profile.username,
                email = profile.email,
                avatarUrl = profile.avatarUrl,
                bio = profile.bio,
                statusMessage = profile.statusMessage,
                isEmailVerified = profile.isEmailVerified,
                lastSeen = profile.lastSeen
            )
            Result.success(profile)
        } catch (e: Exception) {
            val cached = userDao.getUserById(userId)
            if (cached != null) {
                Result.success(
                    UserProfileResponse(
                        id = cached.id,
                        username = cached.userName,
                        email = cached.email,
                        avatarUrl = cached.avatarUrl ?: "",
                        bio = cached.bio,
                        statusMessage = cached.statusMessage,
                        isEmailVerified = cached.isEmailVerified,
                        lastSeen = cached.lastSeen
                    )
                )
            } else {
                Log.e("UserRepo", "Operation failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun uploadAvatar(userId: Int, file: File): Result<String> {
        return try {
            val response = apiService.uploadAvatar(file)
            userDao.updateAvatarUrl(userId, response.avatarUrl)
            Result.success(response.avatarUrl)
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun removeAvatar(userId: Int): Result<String> {
        return try {
            val response = apiService.deleteAvatar()
            userDao.updateAvatarUrl(userId, response.avatarUrl)
            Result.success(response.avatarUrl)
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun changeEmail(userId: Int, currentPassword: String, newEmail: String): Result<Unit> {
        return try {
            val response = apiService.changeEmail(ChangeEmailRequest(newEmail, currentPassword))
            when (response.status) {
                HttpStatusCode.OK -> {
                    userDao.updateEmail(userId, newEmail, false)
                    userPreferences.updateUserEmail(newEmail)
                    Result.success(Unit)
                }
                HttpStatusCode.Unauthorized -> Result.failure(Exception("Incorrect password"))
                HttpStatusCode.Conflict -> Result.failure(Exception("Email already in use"))
                else -> Result.failure(Exception(response.bodyAsText().ifBlank { "Failed to update email" }))
            }
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun sendVerificationEmail(): Result<Unit> {
        return try {
            val response = apiService.sendEmailVerification()
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(Exception(response.bodyAsText().ifBlank { "Failed to send verification email" }))
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun confirmVerificationEmail(userId: Int, token: String): Result<Unit> {
        return try {
            val response = apiService.confirmEmailVerification(token)
            when {
                response.status.isSuccess() -> {
                    userDao.getUserById(userId)?.let { userDao.updateEmail(userId, it.email, true) }
                    Result.success(Unit)
                }
                response.status == HttpStatusCode.Gone -> Result.failure(Exception("Code expired — request a new one"))
                else -> Result.failure(Exception(response.bodyAsText().ifBlank { "Invalid verification code" }))
            }
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun deleteAccountRemote(password: String): Result<Unit> {
        return try {
            val response = apiService.deleteAccount(DeleteAccountRequest(password))
            when (response.status) {
                HttpStatusCode.OK -> Result.success(Unit)
                HttpStatusCode.Unauthorized -> Result.failure(Exception("Incorrect password"))
                else -> Result.failure(Exception(response.bodyAsText().ifBlank { "Failed to delete account" }))
            }
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    // ── User directory: search, public profiles, block, privacy ────────────────
    // Deliberately not Room-cached — this is other people's live server state, not this
    // device's own offline-first data.

    suspend fun searchUsers(query: String): Result<List<SearchUserResult>> {
        return try {
            Result.success(apiService.searchUsers(query))
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun getPublicProfile(userId: Int): Result<PublicProfileResponse> {
        return try {
            Result.success(apiService.getPublicProfile(userId))
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun blockUser(targetUserId: Int): Result<Unit> {
        return try {
            apiService.blockUser(targetUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(targetUserId: Int): Result<Unit> {
        return try {
            apiService.unblockUser(targetUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun getBlockedUsers(): Result<List<SearchUserResult>> {
        return try {
            Result.success(apiService.getBlockedUsers())
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(
        showEmail: Boolean,
        showOnlineStatus: Boolean,
        showLastSeen: Boolean,
        profileVisibility: String
    ): Result<Unit> {
        return try {
            val response = apiService.updatePrivacySettings(
                PrivacySettingsRequest(showEmail, showOnlineStatus, showLastSeen, profileVisibility)
            )
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(Exception(response.bodyAsText().ifBlank { "Failed to update privacy settings" }))
        } catch (e: Exception) {
            Log.e("UserRepo", "Operation failed", e)
            Result.failure(e)
        }
    }
}