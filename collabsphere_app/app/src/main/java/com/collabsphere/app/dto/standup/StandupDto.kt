package com.collabsphere.app.dto.standup

import kotlinx.serialization.Serializable

@Serializable
data class StandupDraft(
    val yesterday: List<String> = emptyList(),
    val today: List<String> = emptyList(),
    val blockers: List<String> = emptyList(),
    val githubLinked: Boolean = false
)
