package com.example.data.remote

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
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

    suspend fun signUp(name: String, email: String, password: String): Result<CloudUser> = safely {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("Account nahi ban saka")
        // The account exists from here on, so a failed name update must not fail the sign up.
        runCatching {
            val change = UserProfileChangeRequest.Builder().setDisplayName(name).build()
            user.updateProfile(change).await()
        }
        CloudUser(user.uid, name, user.email.orEmpty())
    }

    suspend fun signIn(email: String, password: String): Result<CloudUser> = safely {
        val user = auth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Login nahi ho saka")
        user.toCloudUser()
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = safely {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = safely {
        val user = auth.currentUser ?: error("Dobara login karein")
        val credential = EmailAuthProvider.getCredential(user.email.orEmpty(), currentPassword)
        user.reauthenticate(credential).await()
        user.updatePassword(newPassword).await()
        Unit
    }

    fun signOut() = auth.signOut()

    /** Writes the searchable profile if it is not online yet. */
    suspend fun ensureProfile(user: CloudUser): Result<Unit> = safely {
        withTimeout(TIMEOUT_MS) {
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
    suspend fun searchUsers(query: String): Result<List<CloudUser>> = safely {
        val clean = query.trim().lowercase()
        withTimeout(TIMEOUT_MS) {
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
                    .endAt(clean + "")
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

    private suspend fun <T> safely(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: TimeoutCancellationException) {
        Result.failure(Exception("Server se jawab nahi aaya. Internet aur Firestore database check karein."))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(Exception(readableMessage(e)))
    }

    private fun readableMessage(e: Exception): String = when {
        e is FirebaseNetworkException -> "Internet connection check karein"
        e is FirebaseTooManyRequestsException -> "Bohat zyada koshishein. Thori der baad try karein."
        e is FirebaseAuthUserCollisionException -> "Is email se account pehle se bana hua hai"
        e is FirebaseAuthInvalidUserException -> "Account nahi mila ya band hai"
        e is FirebaseAuthInvalidCredentialsException -> "Email ya password ghalat hai"
        e is FirebaseAuthException && e.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
            "Firebase mein Email/Password login Enable nahi hai"
        e.message?.contains("CONFIGURATION_NOT_FOUND") == true ->
            "Firebase mein Authentication set up nahi hai"
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Firestore ijazat nahi de raha. Security rules Publish karein."
        else -> e.message ?: "Kuch ghalat ho gaya"
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: Exception("Request cancel ho gayi"))
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val SEARCH_LIMIT = 20L
        const val MAX_NAME_LENGTH = 50
    }
}
