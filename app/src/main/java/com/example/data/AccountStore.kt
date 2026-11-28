package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class SavedAccount(val uid: String, val name: String, val userId: String, val email: String)

/**
 * Accounts remembered on this phone for quick switching.
 * Passwords are encrypted with a key that never leaves the Android Keystore.
 */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)

    fun list(): List<SavedAccount> = prefs.all.keys.mapNotNull { uid ->
        read(uid)?.let {
            SavedAccount(uid, it.optString("name"), it.optString("userId"), it.optString("email"))
        }
    }.sortedBy { it.name.lowercase() }

    /** [email] is the address used to sign in to Firebase. */
    fun save(uid: String, name: String, email: String, password: String? = null) {
        val entry = read(uid) ?: if (password == null) return else JSONObject()
        entry.put("name", name)
        entry.put("email", email)
        if (password != null) {
            val secret = encrypt(password)
            if (secret == null) entry.remove("secret") else entry.put("secret", secret)
        }
        write(uid, entry)
    }

    fun setUserId(uid: String, userId: String) {
        val entry = read(uid) ?: return
        if (entry.optString("userId") == userId) return
        write(uid, entry.put("userId", userId))
    }

    fun password(uid: String): String? =
        read(uid)?.optString("secret")?.takeIf { it.isNotBlank() }?.let { decrypt(it) }

    fun emailForUserId(userId: String): String? =
        list().firstOrNull { it.userId == userId }?.email?.takeIf { it.isNotBlank() }

    fun remove(uid: String) {
        prefs.edit().remove(uid).apply()
    }

    private fun read(uid: String): JSONObject? =
        prefs.getString(uid, null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun write(uid: String, entry: JSONObject) {
        prefs.edit().putString(uid, entry.toString()).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(text: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(text.toByteArray())
        encode(cipher.iv) + ":" + encode(data)
    }.getOrNull()

    private fun decrypt(secret: String): String? = runCatching {
        val (iv, data) = secret.split(":")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(iv)))
        String(cipher.doFinal(decode(data)))
    }.getOrNull()

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "millionaire_accounts"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
