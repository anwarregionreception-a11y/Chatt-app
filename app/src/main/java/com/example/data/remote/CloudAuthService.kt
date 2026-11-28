package com.example.data.remote

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.withTimeout

data class CloudUser(
    val uid: String,
    val name: String,
    val email: String,
    val profile: PublicProfile? = null,
    val registeredAt: Long = 0L
)

/** What other signed-in users can see. [coins] is shared only when [showCoins] is on. */
data class PublicProfile(
    val name: String,
    val showCoins: Boolean,
    val coins: Long,
    val authorityCoins: Long
)

/**
 * Online accounts and user search.
 *
 * Firestore layout (see firestore.rules):
 *   users/{uid}    -> public profile and name search keywords
 *   emails/{email} -> uid               (exact lookup only, cannot be listed)
 */
class CloudAuthService {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun currentUser(): CloudUser? = auth.currentUser?.toCloudUser()

    suspend fun signUp(name: String, email: String, password: String): Result<CloudUser> = cloudCall {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("Account nahi ban saka")
        // The account exists from here on, so a failed name update must not fail the sign up.
        runCatching {
            val change = UserProfileChangeRequest.Builder().setDisplayName(name).build()
            user.updateProfile(change).await()
        }
        CloudUser(
            user.uid, name, user.email.orEmpty(),
            registeredAt = user.metadata?.creationTimestamp ?: System.currentTimeMillis()
        )
    }

    suspend fun signIn(email: String, password: String): Result<CloudUser> = cloudCall {
        val user = auth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Login nahi ho saka")
        user.toCloudUser()
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = cloudCall {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = cloudCall {
        val user = auth.currentUser ?: error("Dobara login karein")
        val credential = EmailAuthProvider.getCredential(user.email.orEmpty(), currentPassword)
        user.reauthenticate(credential).await()
        user.updatePassword(newPassword).await()
        Unit
    }

    fun signOut() = auth.signOut()

    suspend fun fetchProfile(uid: String): Result<PublicProfile?> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            db.collection("users").document(uid).get().await().toProfile()
        }
    }

    /** Creates or updates the signed-in user's public profile. Writes only when something changed. */
    suspend fun publishProfile(uid: String, email: String, profile: PublicProfile): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            val document = db.collection("users").document(uid)
            val current = document.get().await()
            val name = profile.name.take(MAX_NAME_LENGTH)
            val data = mutableMapOf<String, Any>(
                "name" to name,
                "keywords" to searchKeywords(name),
                "showCoins" to profile.showCoins,
                "authorityCoins" to profile.authorityCoins
            )
            if (profile.showCoins) data["coins"] = profile.coins
            if (current.data != data) {
                val batch = db.batch()
                batch.set(document, data)
                if (!current.exists()) {
                    batch.set(
                        db.collection("emails").document(email.lowercase()),
                        mapOf("uid" to uid)
                    )
                }
                batch.commit().await()
            }
            Unit
        }
    }

    /** Search by full email address, or by the beginning of any part of a name. */
    suspend fun searchUsers(query: String): Result<List<CloudUser>> = cloudCall {
        val clean = normalise(query)
        withTimeout(CLOUD_TIMEOUT_MS) {
            if (clean.contains("@")) {
                if (clean.contains("/")) return@withTimeout emptyList()
                val uid = db.collection("emails").document(clean).get().await().getString("uid")
                    ?: return@withTimeout emptyList()
                val profile = db.collection("users").document(uid).get().await().toProfile()
                    ?: return@withTimeout emptyList()
                listOf(CloudUser(uid, profile.name, clean, profile))
            } else {
                db.collection("users")
                    .whereArrayContains("keywords", clean.take(MAX_KEYWORD_LENGTH))
                    .limit(SEARCH_LIMIT)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { doc ->
                        doc.toProfile()?.let { CloudUser(doc.id, it.name, "", it) }
                    }
            }
        }
    }

    private fun DocumentSnapshot.toProfile(): PublicProfile? {
        val name = getString("name") ?: return null
        return PublicProfile(
            name = name,
            showCoins = getBoolean("showCoins") ?: false,
            coins = getLong("coins") ?: 0L,
            authorityCoins = getLong("authorityCoins") ?: 0L
        )
    }

    private fun normalise(text: String): String =
        text.trim().lowercase().split(Regex("\\s+")).joinToString(" ")

    /** "Syeda Sanober" can be found by "sy", "syeda s", "sano" and so on. */
    private fun searchKeywords(name: String): List<String> {
        val clean = normalise(name)
        return (listOf(clean) + clean.split(" "))
            .flatMap { part ->
                (2..minOf(part.length, MAX_KEYWORD_LENGTH)).map { part.take(it) }
            }
            .distinct()
            .take(MAX_KEYWORDS)
    }

    private fun FirebaseUser.toCloudUser(): CloudUser {
        val mail = email.orEmpty()
        val shownName = displayName?.takeIf { it.isNotBlank() } ?: mail.substringBefore("@")
        return CloudUser(uid, shownName, mail, registeredAt = metadata?.creationTimestamp ?: 0L)
    }

    private companion object {
        const val SEARCH_LIMIT = 20L
        const val MAX_NAME_LENGTH = 50
        const val MAX_KEYWORD_LENGTH = 20
        const val MAX_KEYWORDS = 100
    }
}
