package com.collabsphere.app.model

import androidx.work.WorkInfo
import androidx.work.WorkManager

enum class RetryOutcome { SENT, ALREADY_SENDING, STILL_OFFLINE }

fun isWorkRunning(workManager: WorkManager, uniqueWorkName: String): Boolean =
    runCatching {
        workManager.getWorkInfosForUniqueWork(uniqueWorkName).get().any { it.state == WorkInfo.State.RUNNING }
    }.getOrDefault(false)

fun retryOutcomeMessage(outcome: RetryOutcome): String? = when (outcome) {
    RetryOutcome.SENT -> null
    RetryOutcome.ALREADY_SENDING -> "Already sending — hang tight."
    RetryOutcome.STILL_OFFLINE -> "Still can't reach the server. We'll keep trying in the background."
}
