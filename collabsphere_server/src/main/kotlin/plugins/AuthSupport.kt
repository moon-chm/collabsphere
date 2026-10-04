package plugins

import com.collabsphere.model.UsersTable
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared rules for the authentication & user-account routes, so registration, password reset,
 * profile updates and email changes all enforce the same constraints.
 */
internal object AuthRules {
    const val PASSWORD_MIN_LENGTH = 6
    /** bcrypt silently ignores (or, with the strict strategy, rejects) input past 72 bytes. */
    const val PASSWORD_MAX_BYTES = 72
    const val USERNAME_MIN_LENGTH = 3
    const val USERNAME_MAX_LENGTH = 50
    const val BIO_MAX_LENGTH = 1000
    const val STATUS_MAX_LENGTH = 255
    const val EMAIL_MAX_LENGTH = 255

    const val OTP_TTL_MS = 15 * 60 * 1000L
    /** Minimum gap between two codes issued for the same account/email. */
    const val OTP_RESEND_COOLDOWN_MS = 60 * 1000L
    /** Wrong guesses allowed against a single issued code before it is burned. */
    const val OTP_MAX_ATTEMPTS = 5

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    private val secureRandom = SecureRandom()

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun isValidEmail(normalized: String): Boolean =
        normalized.length <= EMAIL_MAX_LENGTH && EMAIL_REGEX.matches(normalized)

    /** Returns an error message, or null when the password is acceptable. */
    fun passwordError(password: String): String? = when {
        password.length < PASSWORD_MIN_LENGTH -> "Password must be at least $PASSWORD_MIN_LENGTH characters."
        password.toByteArray(Charsets.UTF_8).size > PASSWORD_MAX_BYTES -> "Password is too long (max $PASSWORD_MAX_BYTES bytes)."
        else -> null
    }

    /** Cryptographically secure 6-digit code — kotlin.random is predictable and must not mint secrets. */
    fun generateOtp(): String = String.format("%06d", secureRandom.nextInt(1_000_000))

    /** True when a code issued at (expiresAt - TTL) is still inside the resend cooldown. */
    fun inResendCooldown(existingExpiresAt: Long?, now: Long = System.currentTimeMillis()): Boolean =
        existingExpiresAt != null && now < existingExpiresAt - OTP_TTL_MS + OTP_RESEND_COOLDOWN_MS
}

/** Postgres unique_violation — a concurrent insert/update won the race for a unique key. */
internal fun Throwable.isUniqueViolation(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if ((current as? java.sql.SQLException)?.sqlState == "23505") return true
        current = current.cause
    }
    return false
}

/**
 * JWTs are stateless, so a password change/reset or account deletion would otherwise leave every
 * previously issued token valid for its full 30-day lifetime. Each token carries the user's
 * `token_version`; bumping it (or deleting the user) revokes all older tokens. Lookups are cached
 * briefly so authentication doesn't cost a query per request.
 */
internal object TokenVersions {
    private const val TTL_MS = 30_000L
    private data class Entry(val version: Int?, val fetchedAt: Long)
    private val cache = ConcurrentHashMap<Int, Entry>()

    /** Current version for [userId], or null when the account no longer exists. */
    suspend fun current(userId: Int): Int? {
        val now = System.currentTimeMillis()
        cache[userId]?.takeIf { now - it.fetchedAt < TTL_MS }?.let { return it.version }
        val version = dbQuery {
            UsersTable.select(UsersTable.tokenVersion)
                .where { UsersTable.id eq userId }
                .singleOrNull()
                ?.get(UsersTable.tokenVersion)
        }
        cache[userId] = Entry(version, now)
        return version
    }

    fun invalidate(userId: Int) {
        cache.remove(userId)
    }
}
