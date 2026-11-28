package com.example.data.remote

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Data that is loading (both null), loaded, or failed. */
data class Live<T>(val data: T? = null, val error: String? = null) {
    val isLoading: Boolean get() = data == null && error == null
}

/** Listener errors are delivered as values so that a screen never crashes because of them. */
internal fun <T> Query.live(map: (QuerySnapshot) -> T): Flow<Live<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            trySend(Live<T>(error = readableCloudError(error)))
        } else if (snapshot != null) {
            trySend(Live(map(snapshot)))
        }
    }
    awaitClose { registration.remove() }
}

internal fun <T> DocumentReference.live(map: (DocumentSnapshot) -> T): Flow<Live<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            trySend(Live<T>(error = readableCloudError(error)))
        } else if (snapshot != null) {
            trySend(Live(map(snapshot)))
        }
    }
    awaitClose { registration.remove() }
}

/** Server time of a field; a write that is still on its way counts as "now". */
internal fun DocumentSnapshot.timeOf(field: String): Long =
    getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L
