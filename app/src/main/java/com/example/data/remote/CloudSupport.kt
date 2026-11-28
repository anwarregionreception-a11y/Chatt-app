package com.example.data.remote

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

internal const val CLOUD_TIMEOUT_MS = 10_000L

/** Runs a Firebase call and turns any error into a message the user can read. */
internal suspend fun <T> cloudCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: TimeoutCancellationException) {
    Result.failure(Exception("Server se jawab nahi aaya. Internet aur Firestore database check karein."))
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(Exception(readableCloudError(e)))
}

internal fun readableCloudError(e: Throwable): String = when {
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
        "Ijazat nahi mili. Firestore ke naye rules Publish karein."
    else -> e.message ?: "Kuch ghalat ho gaya"
}

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(task.exception ?: Exception("Request cancel ho gayi"))
        }
    }
}
