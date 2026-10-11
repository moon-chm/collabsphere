package com.collabsphere.app.model

enum class OutboxSyncStatus {
    PENDING,
    SYNCED,
    FAILED_PERMANENT
}
