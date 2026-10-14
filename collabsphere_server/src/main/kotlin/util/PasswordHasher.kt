package com.collabsphere.util

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PasswordHasher {
    private const val COST = 12
    private val BCRYPT_PREFIX = Regex("^\\$2[aby]?\\$")

    fun hash(plain: String): String =
        BCrypt.withDefaults().hashToString(COST, plain.toCharArray())

    fun isHashed(stored: String): Boolean = BCRYPT_PREFIX.containsMatchIn(stored)

    /**
     * True if [plain] matches [stored]. Verifies as a bcrypt hash when [stored] looks like one;
     * otherwise falls back to a direct comparison for rows written before hashing was introduced.
     * Callers on the login path should rehash and persist the password after a legacy match.
     */
    fun matches(plain: String, stored: String): Boolean =
        if (isHashed(stored)) {
            try {
                BCrypt.verifyer().verify(plain.toCharArray(), stored).verified
            } catch (e: IllegalArgumentException) {
                false
            }
        } else {
            stored == plain
        }

    /**
     * bcrypt at cost 12 is ~250ms of CPU by design. Request handlers use these variants so the work
     * never runs on a request-handling thread, and the bounded pool keeps a burst of logins from
     * occupying every core while other requests wait.
     *
     * Never call them inside dbQuery/dbReadQuery: switching dispatchers inside an Exposed suspended
     * transaction closes its connection ("Connection is closed"). Hash before the transaction, or
     * use the blocking variants there (those blocks already run on the IO dispatcher).
     */
    private val bcryptDispatcher = Dispatchers.Default.limitedParallelism(
        (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    )

    suspend fun hashAsync(plain: String): String = withContext(bcryptDispatcher) { hash(plain) }

    suspend fun matchesAsync(plain: String, stored: String): Boolean =
        withContext(bcryptDispatcher) { matches(plain, stored) }
}
