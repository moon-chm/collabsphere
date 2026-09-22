package com.collabsphere.app.model

import at.favre.lib.crypto.bcrypt.BCrypt

/** Hashes passwords before they ever touch local storage (Room), so the on-device DB never holds plaintext. */
object PasswordHasher {
    private const val COST = 12
    private val BCRYPT_PREFIX = Regex("^\\$2[aby]?\\$")

    fun hash(plain: String): String =
        BCrypt.withDefaults().hashToString(COST, plain.toCharArray())

    fun isHashed(stored: String): Boolean = BCRYPT_PREFIX.containsMatchIn(stored)

    /**
     * True if [plain] matches [stored]. Verifies as a bcrypt hash when [stored] looks like one;
     * otherwise falls back to a direct comparison so devices with a pre-existing plaintext-cached
     * password (from before hashing was introduced) can still log in once, offline.
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
