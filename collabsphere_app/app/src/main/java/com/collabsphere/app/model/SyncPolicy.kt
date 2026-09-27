package com.collabsphere.app.model

import androidx.work.ListenableWorker
import com.collabsphere.app.remote.ApiStatusException

enum class SyncDecision { DONE, RETRY, DROP }

/**
 * One rule for every offline-sync path: what to do with a server answer (or a failure to get one).
 * Retrying a permanent rejection (403, 400…) just loops forever in WorkManager, while treating a
 * transient 502 from a cold-starting server as success silently loses the change — both happened before.
 */
object SyncPolicy {

    /** Shown when the server answered but refused a change (not a connectivity problem). */
    const val REFUSED_MESSAGE = "The server didn't accept that change — you may not have permission."

    fun forStatus(statusCode: Int, isDelete: Boolean): SyncDecision = when {
        statusCode in 200..299 -> SyncDecision.DONE
        isDelete && (statusCode == 404 || statusCode == 410) -> SyncDecision.DONE
        statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500 -> SyncDecision.RETRY
        else -> SyncDecision.DROP
    }

    fun forFailure(error: Throwable, isDelete: Boolean): SyncDecision = when (error) {
        is ApiStatusException -> forStatus(error.statusCode, isDelete)
        else -> SyncDecision.RETRY
    }

    fun toWorkResult(decision: SyncDecision): ListenableWorker.Result = when (decision) {
        SyncDecision.DONE -> ListenableWorker.Result.success()
        SyncDecision.RETRY -> ListenableWorker.Result.retry()
        SyncDecision.DROP -> ListenableWorker.Result.failure()
    }
}
