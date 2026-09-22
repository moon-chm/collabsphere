package com.collabsphere.app

/**
 * In-memory mirror of the current session's JWT, kept in sync with [UserPreferences]'s DataStore-backed
 * copy. HttpClient request builders need the token synchronously (they aren't suspend functions), so this
 * holder — not a suspend DataStore read — is what gets attached to every outgoing request.
 */
object AuthTokenHolder {
    @Volatile
    var token: String? = null
}
