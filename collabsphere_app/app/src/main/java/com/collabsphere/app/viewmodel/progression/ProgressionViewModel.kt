package com.collabsphere.app.viewmodel.progression

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.dto.progression.BadgeEvidenceResponse
import com.collabsphere.app.dto.progression.BadgeFamilyDto
import com.collabsphere.app.dto.progression.CareerLevelDto
import com.collabsphere.app.dto.progression.LedgerEntryResponse
import com.collabsphere.app.model.progression.AchievementFeedback
import com.collabsphere.app.model.progression.BadgeProgressEntity
import com.collabsphere.app.model.progression.ProgressionFeedback
import com.collabsphere.app.model.progression.ProgressionRepo
import com.collabsphere.app.model.progression.ProgressionSummaryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BadgeTierUi { BRONZE, SILVER, GOLD }

/** One badge family as the UI shows it: definition plus this user's progress. */
data class BadgeUi(
    val family: BadgeFamilyDto,
    val counterValue: Long,
    val unlockedAt: Map<BadgeTierUi, Long>,
    val nextTier: BadgeTierUi?,
    val nextThreshold: Long?
) {
    val highestTier: BadgeTierUi? get() = BadgeTierUi.entries.lastOrNull { it in unlockedAt }
    val isLocked: Boolean get() = unlockedAt.isEmpty()
    fun threshold(tier: BadgeTierUi): Long = when (tier) {
        BadgeTierUi.BRONZE -> family.bronze
        BadgeTierUi.SILVER -> family.silver
        BadgeTierUi.GOLD -> family.gold
    }
}

data class CareerUiState(
    val summary: ProgressionSummaryEntity? = null,
    val levels: List<CareerLevelDto> = emptyList(),
    val badges: List<BadgeUi> = emptyList(),
    /** Reward rule key to its human description, for the SP history. */
    val ruleDescriptions: Map<String, String> = emptyMap(),
    val isRefreshing: Boolean = false,
    /** True when the last refresh failed; cached data (if any) is still shown. */
    val refreshFailed: Boolean = false
) {
    val hasData: Boolean get() = summary != null
    /** 0..1 progress toward the next level; 1 at the maximum level (no division by zero). */
    val levelProgress: Float
        get() {
            val s = summary ?: return 0f
            if (s.isMaxLevel || s.spSpanToNext <= 0) return 1f
            return (s.spIntoLevel.toFloat() / s.spSpanToNext).coerceIn(0f, 1f)
        }
}

data class HistoryState(
    val entries: List<LedgerEntryResponse> = emptyList(),
    val nextBeforeId: Long? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val loadedOnce: Boolean = false
) {
    val canLoadMore: Boolean get() = !loadedOnce || nextBeforeId != null
}

sealed interface EvidenceState {
    data object Idle : EvidenceState
    data object Loading : EvidenceState
    data class Loaded(val familyKey: String, val events: List<BadgeEvidenceResponse>) : EvidenceState
    data object Failed : EvidenceState
}

sealed interface GitHubIdentityState {
    data object Unknown : GitHubIdentityState
    data object Loading : GitHubIdentityState
    data class Linked(val login: String?) : GitHubIdentityState
    data object NotLinked : GitHubIdentityState
    data object Failed : GitHubIdentityState
}

class ProgressionViewModel(
    private val userId: Int,
    private val repo: ProgressionRepo
) : ViewModel() {

    private val refreshState = MutableStateFlow(false to false) // (refreshing, failed)

    val uiState: StateFlow<CareerUiState> = combine(
        repo.observeSummary(userId),
        repo.observeBadges(userId),
        repo.observeCatalog(),
        refreshState
    ) { summary, badges, catalog, (refreshing, failed) ->
        CareerUiState(
            summary = summary,
            levels = catalog?.levels.orEmpty(),
            badges = buildBadges(catalog?.badges.orEmpty(), badges),
            ruleDescriptions = catalog?.rules?.associate { it.key to it.description }.orEmpty(),
            isRefreshing = refreshing,
            refreshFailed = failed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareerUiState())

    /** Celebrations owed for confirmed server state the user hasn't been shown on this device. */
    val pendingFeedback: StateFlow<List<AchievementFeedback>> = combine(
        repo.observeSeen(userId),
        repo.observeSummary(userId),
        repo.observeBadges(userId),
        repo.observeCatalog()
    ) { seen, summary, badges, catalog ->
        if (seen == null) emptyList() else ProgressionFeedback.pending(seen, summary, badges, catalog)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _history = MutableStateFlow(HistoryState())
    val history: StateFlow<HistoryState> = _history.asStateFlow()

    private val _evidence = MutableStateFlow<EvidenceState>(EvidenceState.Idle)
    val evidence: StateFlow<EvidenceState> = _evidence.asStateFlow()

    fun refresh(force: Boolean = false) {
        if (refreshState.value.first) return
        viewModelScope.launch {
            refreshState.value = true to refreshState.value.second
            val result = repo.refresh(userId, force)
            refreshState.value = false to result.isFailure
        }
    }

    fun acknowledge(feedback: AchievementFeedback) {
        viewModelScope.launch { repo.acknowledge(userId, feedback) }
    }

    fun loadMoreHistory() {
        val current = _history.value
        if (current.isLoading || !current.canLoadMore) return
        _history.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            repo.loadHistory(current.nextBeforeId).fold(
                onSuccess = { page ->
                    _history.update {
                        it.copy(
                            entries = it.entries + page.entries,
                            nextBeforeId = page.nextBeforeId,
                            isLoading = false,
                            loadedOnce = true
                        )
                    }
                },
                onFailure = { _history.update { it.copy(isLoading = false, loadFailed = true) } }
            )
        }
    }

    fun loadEvidence(familyKey: String) {
        _evidence.value = EvidenceState.Loading
        viewModelScope.launch {
            _evidence.value = repo.loadEvidence(familyKey).fold(
                onSuccess = { EvidenceState.Loaded(familyKey, it) },
                onFailure = { EvidenceState.Failed }
            )
        }
    }

    fun clearEvidence() {
        _evidence.value = EvidenceState.Idle
    }

    private val _gitHubIdentity = MutableStateFlow<GitHubIdentityState>(GitHubIdentityState.Unknown)
    val gitHubIdentity: StateFlow<GitHubIdentityState> = _gitHubIdentity.asStateFlow()

    /** One-shot UI effects: a URL to open in the browser, or a message to show. */
    private val _effects = kotlinx.coroutines.channels.Channel<GitHubLinkEffect>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val effects: kotlinx.coroutines.flow.Flow<GitHubLinkEffect> = _effects.receiveAsFlow()

    fun loadGitHubIdentity() {
        if (_gitHubIdentity.value is GitHubIdentityState.Loading) return
        _gitHubIdentity.value = GitHubIdentityState.Loading
        viewModelScope.launch {
            _gitHubIdentity.value = repo.gitHubIdentity().fold(
                onSuccess = { if (it.linked) GitHubIdentityState.Linked(it.login) else GitHubIdentityState.NotLinked },
                onFailure = { GitHubIdentityState.Failed }
            )
        }
    }

    fun startGitHubLink() {
        viewModelScope.launch {
            repo.gitHubLinkUrl().fold(
                onSuccess = { _effects.send(GitHubLinkEffect.OpenUrl(it)) },
                onFailure = { _effects.send(GitHubLinkEffect.Message("GitHub linking isn't available right now.")) }
            )
        }
    }

    fun unlinkGitHub() {
        viewModelScope.launch {
            repo.unlinkGitHub().fold(
                onSuccess = {
                    _gitHubIdentity.value = GitHubIdentityState.NotLinked
                    _effects.send(GitHubLinkEffect.Message("GitHub unlinked. Points you already earned are kept."))
                },
                onFailure = { _effects.send(GitHubLinkEffect.Message("Couldn't unlink GitHub. Please try again.")) }
            )
        }
    }

    companion object {
        /** Joins definitions with progress; families without server progress yet show as locked at zero. */
        fun buildBadges(families: List<BadgeFamilyDto>, progress: List<BadgeProgressEntity>): List<BadgeUi> {
            val byKey = progress.associateBy { it.familyKey }
            return families.map { family ->
                val p = byKey[family.key]
                val unlocked = buildMap {
                    p?.bronzeUnlockedAt?.let { put(BadgeTierUi.BRONZE, it) }
                    p?.silverUnlockedAt?.let { put(BadgeTierUi.SILVER, it) }
                    p?.goldUnlockedAt?.let { put(BadgeTierUi.GOLD, it) }
                }
                val count = p?.counterValue ?: 0L
                BadgeUi(
                    family = family,
                    counterValue = count,
                    unlockedAt = unlocked,
                    nextTier = p?.nextTier?.let { runCatching { BadgeTierUi.valueOf(it) }.getOrNull() }
                        ?: if (p == null) BadgeTierUi.BRONZE else null,
                    nextThreshold = p?.nextThreshold ?: if (p == null) family.bronze else null
                )
            }
        }
    }
}

sealed interface GitHubLinkEffect {
    data class OpenUrl(val url: String) : GitHubLinkEffect
    data class Message(val text: String) : GitHubLinkEffect
}
