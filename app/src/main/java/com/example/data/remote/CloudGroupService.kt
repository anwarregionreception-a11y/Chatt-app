package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

data class CloudGroup(
    val id: String,
    val name: String,
    val description: String,
    val isPublic: Boolean,
    val ownerUid: String,
    val ownerName: String,
    val memberCount: Long
)

data class GroupPerson(val uid: String, val name: String, val time: Long)

data class GroupChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val sentAt: Long
)

/** Wraps a record that may not exist, so that "missing" differs from "still loading". */
data class Maybe<T>(val value: T?)

/**
 * Online groups.
 *
 * Firestore layout (see firestore.rules):
 *   groups/{id}                    -> name, owner, member count
 *   groups/{id}/members/{uid}
 *   groups/{id}/requests/{uid}     -> join requests of private groups
 *   groups/{id}/messages/{id}
 *   groups/{id}/kicks/{uid}        -> moderator kicks
 */
class CloudGroupService {
    private val db = FirebaseFirestore.getInstance()
    private val groups = db.collection("groups")

    private fun group(id: String) = groups.document(id)
    private fun member(groupId: String, uid: String) = group(groupId).collection("members").document(uid)
    private fun request(groupId: String, uid: String) = group(groupId).collection("requests").document(uid)

    fun observeGroups(): Flow<Live<List<CloudGroup>>> =
        groups.orderBy("memberCount", Query.Direction.DESCENDING).limit(LIST_LIMIT)
            .live { snapshot -> snapshot.documents.mapNotNull { it.toGroup() } }

    fun observeGroup(id: String): Flow<Live<Maybe<CloudGroup>>> =
        group(id).live { Maybe(it.toGroup()) }

    suspend fun topGroups(): Result<List<CloudGroup>> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            groups.orderBy("memberCount", Query.Direction.DESCENDING).limit(10)
                .get().await().documents.mapNotNull { it.toGroup() }
        }
    }

    fun observeMembership(groupId: String, uid: String): Flow<Live<Maybe<GroupPerson>>> =
        member(groupId, uid).live { Maybe(it.toPerson("joinedAt")) }

    fun observeOwnRequest(groupId: String, uid: String): Flow<Live<Maybe<GroupPerson>>> =
        request(groupId, uid).live { Maybe(it.toPerson("at")) }

    fun observeMembers(groupId: String): Flow<Live<List<GroupPerson>>> =
        group(groupId).collection("members").live { snapshot ->
            snapshot.documents.mapNotNull { it.toPerson("joinedAt") }.sortedBy { it.name.lowercase() }
        }

    fun observeRequests(groupId: String): Flow<Live<List<GroupPerson>>> =
        group(groupId).collection("requests").live { snapshot ->
            snapshot.documents.mapNotNull { it.toPerson("at") }.sortedBy { it.time }
        }

    fun observeMessages(groupId: String): Flow<Live<List<GroupChatMessage>>> =
        group(groupId).collection("messages")
            .orderBy("sentAt", Query.Direction.DESCENDING)
            .limit(MESSAGE_LIMIT)
            .live { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val sender = doc.getString("senderId")
                    val text = doc.getString("text")
                    if (sender == null || text == null) null
                    else GroupChatMessage(
                        doc.id, sender, doc.getString("senderName").orEmpty(), text, doc.timeOf("sentAt")
                    )
                }
            }

    /** The end time of a kick, or 0. */
    fun observeKick(groupId: String, uid: String): Flow<Live<Long>> =
        group(groupId).collection("kicks").document(uid).live { it.timeOf("until") }

    /** Creating a group costs [price] coins; the app Owner passes 0. */
    suspend fun createGroup(
        ownerUid: String,
        name: String,
        description: String,
        isPublic: Boolean,
        price: Long
    ): Result<String> = cloudCall {
        val cleanName = name.trim().take(40)
        require(cleanName.isNotBlank()) { "Group ka naam likhein" }
        withTimeout(CLOUD_TIMEOUT_MS) {
            val myName = userName(ownerUid)
            val wallet = db.collection("wallets").document(ownerUid)
            if (price > 0) {
                val coins = wallet.get().await().getLong("coins") ?: 0L
                check(coins >= price) { "Group ke liye $price coins chahiye" }
            }
            val target = groups.document()
            db.runBatch { batch ->
                batch.set(
                    target,
                    mapOf(
                        "name" to cleanName,
                        "description" to description.trim().take(200),
                        "isPublic" to isPublic,
                        "ownerUid" to ownerUid,
                        "ownerName" to myName,
                        "memberCount" to 1L,
                        "lastMember" to ownerUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(member(target.id, ownerUid), memberData(myName))
                if (price > 0) {
                    batch.update(
                        wallet,
                        mapOf("coins" to FieldValue.increment(-price), "lastGroupId" to target.id)
                    )
                }
            }.await()
            target.id
        }
    }

    suspend fun join(groupId: String, uid: String): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            val name = userName(uid)
            db.runBatch { batch ->
                batch.set(member(groupId, uid), memberData(name))
                batch.update(group(groupId), counter(uid, 1))
            }.await()
            Unit
        }
    }

    suspend fun requestToJoin(groupId: String, uid: String): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            request(groupId, uid).set(
                mapOf("name" to userName(uid), "at" to FieldValue.serverTimestamp())
            ).await()
            Unit
        }
    }

    suspend fun approve(groupId: String, uid: String): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            val name = userName(uid)
            db.runBatch { batch ->
                batch.delete(request(groupId, uid))
                batch.set(member(groupId, uid), memberData(name))
                batch.update(group(groupId), counter(uid, 1))
            }.await()
            Unit
        }
    }

    suspend fun reject(groupId: String, uid: String): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            request(groupId, uid).delete().await()
            Unit
        }
    }

    /** Leaving a group, or the group owner removing a member. */
    suspend fun removeMember(groupId: String, uid: String): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            db.runBatch { batch ->
                batch.delete(member(groupId, uid))
                batch.update(group(groupId), counter(uid, -1))
            }.await()
            Unit
        }
    }

    suspend fun sendMessage(groupId: String, uid: String, senderName: String, text: String): Result<Unit> =
        cloudCall {
            val write = group(groupId).collection("messages").document().set(
                mapOf(
                    "senderId" to uid,
                    "senderName" to senderName,
                    "text" to text,
                    "sentAt" to FieldValue.serverTimestamp()
                )
            )
            // Without internet the write stays queued, so a timeout is not an error.
            withTimeoutOrNull(CLOUD_TIMEOUT_MS) { write.await() }
            Unit
        }

    suspend fun deleteMessage(groupId: String, messageId: String): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            group(groupId).collection("messages").document(messageId).delete().await()
            Unit
        }
    }

    /** Moderator: removes a user from this group's chat for two hours. */
    suspend fun kick(groupId: String, targetUid: String, byUid: String): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            group(groupId).collection("kicks").document(targetUid).set(
                mapOf(
                    "until" to Timestamp(Date(System.currentTimeMillis() + PENALTY_MS)),
                    "by" to byUid
                )
            ).await()
            Unit
        }
    }

    suspend fun wasKicked(groupId: String, uid: String): Result<Boolean> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            group(groupId).collection("kicks").document(uid).get().await().exists()
        }
    }

    private suspend fun userName(uid: String): String =
        db.collection("users").document(uid).get().await().getString("name")
            ?: error("Profile abhi online save nahi hui. Thori der baad try karein.")

    private fun memberData(name: String) =
        mapOf("name" to name, "joinedAt" to FieldValue.serverTimestamp())

    private fun counter(uid: String, step: Long) =
        mapOf("memberCount" to FieldValue.increment(step), "lastMember" to uid)

    private fun DocumentSnapshot.toGroup(): CloudGroup? {
        val name = getString("name") ?: return null
        return CloudGroup(
            id = id,
            name = name,
            description = getString("description").orEmpty(),
            isPublic = getBoolean("isPublic") ?: true,
            ownerUid = getString("ownerUid").orEmpty(),
            ownerName = getString("ownerName").orEmpty(),
            memberCount = getLong("memberCount") ?: 0L
        )
    }

    private fun DocumentSnapshot.toPerson(timeField: String): GroupPerson? {
        val name = getString("name") ?: return null
        return GroupPerson(id, name, timeOf(timeField))
    }

    companion object {
        const val PENALTY_MS = 2L * 60L * 60L * 1000L
        private const val LIST_LIMIT = 200L
        private const val MESSAGE_LIMIT = 100L
    }
}
