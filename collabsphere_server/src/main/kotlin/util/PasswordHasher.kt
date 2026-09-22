package com.collabsphere.util

import at.favre.lib.crypto.bcrypt.BCrypt

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
}
