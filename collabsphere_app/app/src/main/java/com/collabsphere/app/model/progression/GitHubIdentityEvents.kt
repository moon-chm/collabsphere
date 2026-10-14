package com.collabsphere.app.model.progression

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Outcome of the identity-only GitHub link, delivered by the `collabsphere://github-identity` deep link. */
data class GitHubIdentityResult(val login: String?, val error: String?)

/**
 * Bridges the OAuth redirect (received by MainActivity) to the profile's career section. Separate
 * from the workspace GitHub connect flow, which uses its own deep-link host.
 */
object GitHubIdentityEvents {
    private val _result = MutableStateFlow<GitHubIdentityResult?>(null)
    val result: StateFlow<GitHubIdentityResult?> = _result.asStateFlow()

    fun publish(uri: Uri?): Boolean {
        if (uri?.scheme != "collabsphere" || uri.host != "github-identity") return false
        _result.value = GitHubIdentityResult(
            login = uri.getQueryParameter("login"),
            error = uri.getQueryParameter("error") ?: if (uri.getQueryParameter("status") == "linked") null else "unknown"
        )
        return true
    }

    fun consume() {
        _result.value = null
    }

    /** User-facing message for a link outcome. */
    fun message(result: GitHubIdentityResult): String = when (result.error) {
        null -> "GitHub linked as @${result.login}. Your merged work now counts toward your career."
        "github_account_in_use" -> "That GitHub account is already linked to another CollabSphere user."
        "access_denied" -> "GitHub linking was cancelled."
        else -> "Couldn't link GitHub. Please try again."
    }
}
