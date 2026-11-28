package com.example.data.remote

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.withTimeout

data class CloudUser(val uid: String, val name: String, val email: String)

/**
 * Online accounts and user search.
 *
 * Firestore layout (see firestore.rules):
 *   users/{uid}    -> name, nameLower   (searchable by signed-in users)
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
        CloudUser(user.uid, name, user.email.orEmpty())
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

    /** Writes the searchable profile if it is not online yet. */
    suspend fun ensureProfile(user: CloudUser): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            val profile = db.collection("users").document(user.uid)
            if (!profile.get().await().exists()) {
                val name = user.name.take(MAX_NAME_LENGTH)
                val batch = db.batch()
                batch.set(profile, mapOf("name" to name, "nameLower" to name.lowercase()))
                batch.set(
                    db.collection("emails").document(user.email.lowercase()),
                    mapOf("uid" to user.uid)
                )
                batch.commit().await()
            }
            Unit
        }
    }

    /** Search by full email address, or by the beginning of a name. */
    suspend fun searchUsers(query: String): Result<List<CloudUser>> = cloudCall {
        val clean = query.trim().lowercase()
        withTimeout(CLOUD_TIMEOUT_MS) {
            if (clean.contains("@")) {
                if (clean.contains("/")) return@withTimeout emptyList()
                val uid = db.collection("emails").document(clean).get().await().getString("uid")
                    ?: return@withTimeout emptyList()
                val name = db.collection("users").document(uid).get().await().getString("name")
                    ?: return@withTimeout emptyList()
                listOf(CloudUser(uid, name, clean))
            } else {
                db.collection("users")
                    .orderBy("nameLower")
                    .startAt(clean)
                    .endAt(clean + "\uf8ff")
                    .limit(SEARCH_LIMIT)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { doc ->
                        doc.getString("name")?.let { CloudUser(doc.id, it, "") }
                    }
            }
        }
    }

    private fun FirebaseUser.toCloudUser(): CloudUser {
        val mail = email.orEmpty()
        val shownName = displayName?.takeIf { it.isNotBlank() } ?: mail.substringBefore("@")
        return CloudUser(uid, shownName, mail)
    }

    private companion object {
        const val SEARCH_LIMIT = 20L
        const val MAX_NAME_LENGTH = 50
    }
}
