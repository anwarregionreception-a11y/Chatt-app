package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import kotlinx.coroutines.withTimeout

data class PresenceUser(
    val uid: String,
    val name: String,
    val deviceId: String,
    val registeredAt: Long,
    val lastSeen: Long
)

data class UserDirectory(val total: Long, val online: Long, val users: List<PresenceUser>)

/**
 * "Last seen" records, readable only by the Owner (see firestore.rules).
 *   presence/{uid} -> name, deviceId, createdAt, lastSeen
 */
class CloudPresenceService {
    private val presence = FirebaseFirestore.getInstance().collection("presence")

    suspend fun heartbeat(uid: String, name: String, deviceId: String, registeredAt: Long): Result<Unit> =
        cloudCall {
            withTimeout(CLOUD_TIMEOUT_MS) {
                presence.document(uid).set(
                    mapOf(
                        "name" to name.take(50),
                        "deviceId" to deviceId.take(64),
                        "createdAt" to registeredAt,
                        "lastSeen" to FieldValue.serverTimestamp()
                    )
                ).await()
                Unit
            }
        }

    /** Owner only. Loads totals and the most recently active [limit] users. */
    suspend fun loadDirectory(limit: Long): Result<UserDirectory> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            val since = Timestamp(Date(System.currentTimeMillis() - ONLINE_WINDOW_MS))
            val total = presence.count().get(AggregateSource.SERVER).await().count
            val online = presence.whereGreaterThan("lastSeen", since)
                .count().get(AggregateSource.SERVER).await().count
            val users = presence.orderBy("lastSeen", Query.Direction.DESCENDING)
                .limit(limit).get().await()
                .documents.mapNotNull { it.toPresenceUser() }
            UserDirectory(total, online, users)
        }
    }

    /** Owner only. Every account that was last used on the given device. */
    suspend fun accountsOnDevice(deviceId: String): Result<List<PresenceUser>> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            presence.whereEqualTo("deviceId", deviceId).get().await()
                .documents.mapNotNull { it.toPresenceUser() }
        }
    }

    private fun DocumentSnapshot.toPresenceUser(): PresenceUser? {
        val name = getString("name") ?: return null
        return PresenceUser(
            uid = id,
            name = name,
            deviceId = getString("deviceId").orEmpty(),
            registeredAt = getLong("createdAt") ?: 0L,
            lastSeen = getTimestamp("lastSeen")?.toDate()?.time ?: 0L
        )
    }

    companion object {
        /** The app reports every 5 minutes while open; allow one minute of delay. */
        const val ONLINE_WINDOW_MS = 6L * 60L * 1000L
        const val HEARTBEAT_MS = 5L * 60L * 1000L
    }
}
