package com.example.rohit_project_challlange.model

import android.util.Log
import com.example.rohit_project_challlange.dto.notification.NotificationCountResponse
import com.example.rohit_project_challlange.dto.notification.NotificationResponse
import com.example.rohit_project_challlange.remote.notification.NotificationApiService

/**
 * Deliberately not Room-backed — the notification list is server-authoritative and inherently
 * live (real-time pushes arrive via [com.example.rohit_project_challlange.NotificationCenter]),
 * unlike the rest of the app's offline-first entities.
 */
class NotificationRepo(private val apiService: NotificationApiService) {

    suspend fun getNotifications(unreadOnly: Boolean = false): Result<List<NotificationResponse>> {
        return try {
            Result.success(apiService.getNotifications(unreadOnly = unreadOnly))
        } catch (e: Exception) {
            Log.e("NotificationRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun getCount(): Result<NotificationCountResponse> {
        return try {
            Result.success(apiService.getCount())
        } catch (e: Exception) {
            Log.e("NotificationRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun markRead(ids: List<Int>? = null): Result<Unit> {
        return try {
            if (apiService.markRead(ids)) Result.success(Unit)
            else Result.failure(Exception("Failed to mark as read"))
        } catch (e: Exception) {
            Log.e("NotificationRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun deleteOne(id: Int): Result<Unit> {
        return try {
            if (apiService.deleteOne(id)) Result.success(Unit)
            else Result.failure(Exception("Failed to delete notification"))
        } catch (e: Exception) {
            Log.e("NotificationRepo", "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun clearAll(): Result<Unit> {
        return try {
            if (apiService.clearAll()) Result.success(Unit)
            else Result.failure(Exception("Failed to clear notifications"))
        } catch (e: Exception) {
            Log.e("NotificationRepo", "Operation failed", e)
            Result.failure(e)
        }
    }
}
