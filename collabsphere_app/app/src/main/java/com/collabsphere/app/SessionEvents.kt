package com.collabsphere.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionEvents {
    private val _expiredToken = MutableStateFlow<String?>(null)
    val expiredToken: StateFlow<String?> = _expiredToken.asStateFlow()

    @Volatile
    private var handledToken: String? = null

    fun onUnauthorized(rejectedToken: String?) {
        if (rejectedToken.isNullOrBlank()) return
        if (rejectedToken != AuthTokenHolder.token) return
        _expiredToken.value = rejectedToken
    }

    fun claim(token: String?): Boolean = synchronized(this) {
        if (token == null || token == handledToken) return false
        handledToken = token
        true
    }
}
