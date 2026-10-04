package com.collabsphere.util

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.slf4j.LoggerFactory

object CryptoService {
    private val logger = LoggerFactory.getLogger(CryptoService::class.java)
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val PREFIX = "v1:"

    private val rawKey = System.getenv("GITHUB_TOKEN_SECRET")?.takeIf { it.isNotBlank() }
    private val keySpec by lazy {
        rawKey?.let {
            val keyBytes = it.toByteArray().copyOf(32) // Ensure 256-bit
            SecretKeySpec(keyBytes, "AES")
        }
    }
    
    private val secureRandom = SecureRandom()

    fun encrypt(plainText: String?): String? {
        if (plainText.isNullOrBlank()) return plainText
        val key = keySpec ?: return plainText // Fallback if no secret provided
        
        val iv = ByteArray(IV_LENGTH_BYTE)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        
        val cipherText = cipher.doFinal(plainText.toByteArray())
        val combined = iv + cipherText
        return PREFIX + Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(cipherText: String?): String? {
        if (cipherText.isNullOrBlank()) return cipherText
        if (!cipherText.startsWith(PREFIX)) return cipherText // Legacy plaintext
        
        val key = keySpec ?: return cipherText
        
        return try {
            val combined = Base64.getDecoder().decode(cipherText.substring(PREFIX.length))
            if (combined.size < IV_LENGTH_BYTE) return cipherText
            
            val iv = combined.copyOfRange(0, IV_LENGTH_BYTE)
            val encrypted = combined.copyOfRange(IV_LENGTH_BYTE, combined.size)
            
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
            
            String(cipher.doFinal(encrypted))
        } catch (e: Exception) {
            logger.error("[CryptoService] Decryption failed", e)
            null
        }
    }
}
