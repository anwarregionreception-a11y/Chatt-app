package com.example.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

data class CloudFriendship(
    val id: String,
    val members: List<String>,
    val requestedBy: String,
    val status: String,
    val names: Map<String, String>,
    val createdAt: Long
)

data class CloudFriendshipList(val items: List<CloudFriendship>, val fromServer: Boolean)

data class CloudMessage(val id: String, val senderId: String, val text: String, val sentAt: Long)

/**
 * Friend requests and one-to-one messages.
 *
 * Firestore layout (see firestore.rules):
 *   friendships/{uidA_uidB}             -> one document per pair
 *   inbox/{receiverUid}/messages/{id}   -> waits here until the receiver's phone saves it
 */
class CloudChatService {
    private val db = FirebaseFirestore.getInstance()

    fun observeFriendships(uid: String): Flow<CloudFriendshipList> = callbackFlow {
        val registration = db.collection("friendships")
            .whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        val members = (doc.get("members") as? List<*>)?.filterIsInstance<String>()
                        val requestedBy = doc.getString("requestedBy")
                        val status = doc.getString("status")
                        if (members == null || members.size != 2 || requestedBy == null || status == null) {
                            null
                        } else {
                            val names = (doc.get("names") as? Map<*, *>)?.entries
                                ?.associate { it.key.toString() to it.value.toString() }
                                ?: emptyMap()
                            CloudFriendship(
                                doc.id, members, requestedBy, status, names,
                                doc.getLong("createdAt") ?: 0L
                            )
                        }
                    }
                    trySend(CloudFriendshipList(items, !snapshot.metadata.isFromCache))
                }
            }
        awaitClose { registration.remove() }
    }.buffer(Channel.UNLIMITED)

    suspend fun sendRequest(fromUid: String, toUid: String, toName: String): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            val myName = db.collection("users").document(fromUid).get().await().getString("name")
                ?: error("Aap ka profile online save nahi hua. Dobara login karein.")
            val members = listOf(fromUid, toUid).sorted()
            db.collection("friendships").document(members.joinToString("_")).set(
                mapOf(
                    "members" to members,
                    "requestedBy" to fromUid,
                    "status" to "PENDING",
                    "names" to mapOf(fromUid to myName, toUid to toName),
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
            Unit
        }
    }

    suspend fun acceptRequest(pairId: String): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            db.collection("friendships").document(pairId).update("status", "ACCEPTED").await()
            Unit
        }
    }

    suspend fun removeFriendship(pairId: String): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            db.collection("friendships").document(pairId).delete().await()
            Unit
        }
    }

    suspend fun sendMessage(
        fromUid: String,
        toUid: String,
        pairId: String,
        text: String
    ): Result<CloudMessage> = cloudCall {
        val document = db.collection("inbox").document(toUid).collection("messages").document()
        val message = CloudMessage(document.id, fromUid, text, System.currentTimeMillis())
        val write = document.set(
            mapOf(
                "senderId" to message.senderId,
                "pairId" to pairId,
                "text" to message.text,
                "sentAt" to message.sentAt
            )
        )
        // Without internet Firestore keeps the write queued, so a timeout is not an error.
        withTimeoutOrNull(CLOUD_TIMEOUT_MS) { write.await() }
        message
    }

    fun observeInbox(uid: String): Flow<List<CloudMessage>> = callbackFlow {
        val registration = db.collection("inbox").document(uid).collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(
                        snapshot.documents.mapNotNull { doc ->
                            val sender = doc.getString("senderId")
                            val text = doc.getString("text")
                            if (sender == null || text == null) null
                            else CloudMessage(doc.id, sender, text, doc.getLong("sentAt") ?: 0L)
                        }
                    )
                }
            }
        awaitClose { registration.remove() }
    }.buffer(Channel.UNLIMITED)

    /** Deletes messages that are already saved on this phone. */
    fun removeDelivered(uid: String, messageIds: List<String>) {
        val messages = db.collection("inbox").document(uid).collection("messages")
        messageIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(messages.document(it)) }
            batch.commit()
        }
    }
}
