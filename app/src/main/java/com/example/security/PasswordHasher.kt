package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 12000
    private const val KEY_LENGTH = 256
    private const val SALT_BYTES = 16
    private const val TEMP_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val secureRandom = SecureRandom()

    fun hashPassword(rawPassword: String): String {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val hash = pbkdf2(rawPassword.toCharArray(), salt)
        return "${salt.toHex()}:${hash.toHex()}"
    }

    fun verifyPassword(rawPassword: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return false
        val parts = storedHash.split(":")
        if (parts.size != 2) return false
        return try {
            val salt = parts[0].fromHex()
            val expectedHash = parts[1].fromHex()
            val actualHash = pbkdf2(rawPassword.toCharArray(), salt)
            MessageDigest.isEqual(expectedHash, actualHash)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun generateTemporaryPassword(): String {
        return buildString(6) {
            repeat(6) {
                append(TEMP_CHARS[secureRandom.nextInt(TEMP_CHARS.length)])
            }
        }
    }

    fun validateNewPassword(password: String): String? {
        if (password.length < 8) {
            return "Password must be at least 8 characters long"
        }
        if (password.none { it.isUpperCase() }) {
            return "Password must contain at least 1 capital letter"
        }
        if (password.none { it.isDigit() }) {
            return "Password must contain at least 1 number"
        }
        return null
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray {
        require(length % 2 == 0) { "Invalid hex length" }
        return ByteArray(length / 2) { i ->
            substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
