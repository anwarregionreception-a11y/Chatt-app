package com.example.data.remote

import com.example.domain.SpinReward
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout

enum class StaffRole(val title: String, val duties: String) {
    MODERATOR(
        "Moderator",
        "A Moderator keeps group chats respectful. If a member misbehaves or uses abusive " +
            "language, the Moderator can remove that member from the group chat for 2 hours. " +
            "If the member misbehaves again, the Moderator can ban that ID from chatting in " +
            "every group for 2 hours."
    ),
    COP(
        "Cop",
        "A Cop watches for illegal activity, such as selling weapons or drugs, or any other " +
            "unlawful act. The Cop sends the user's ID with a report to the app Owner, who " +
            "reviews it and takes action."
    ),
    MENTOR(
        "Mentor",
        "A Mentor answers questions about the app and helps users learn how to use it."
    );

    companion object {
        fun from(value: String?): StaffRole? = entries.firstOrNull { it.name == value }
    }
}

data class StaffMember(val uid: String, val name: String, val role: StaffRole)

data class StaffApplication(
    val uid: String,
    val name: String,
    val userId: String,
    val role: StaffRole,
    val note: String
)

data class CopReport(
    val id: String,
    val copName: String,
    val targetUid: String,
    val targetName: String,
    val targetUserId: String,
    val reason: String,
    val createdAt: Long
)

data class ChatBan(val until: Long, val allChat: Boolean) {
    val isActive: Boolean get() = until > System.currentTimeMillis()
}

data class Announcement(val id: String, val title: String, val text: String, val createdAt: Long)

data class PaymentMethod(
    val id: String,
    val name: String,
    val account: String,
    val holder: String,
    val instructions: String,
    val minCoins: Long
)

data class TopUpOrder(
    val id: String,
    val uid: String,
    val name: String,
    val method: String,
    val coins: Long,
    val amountPkr: Long,
    val reference: String,
    val status: String,
    val createdAt: Long
)

data class Buyer(val uid: String, val name: String, val totalCoins: Long)

data class SpinStatus(val lastSpin: Long, val claimed: Boolean)

/** Staff, reports, bans, Owner messages, top-ups and the daily spin (see firestore.rules). */
class CloudCommunityService {
    private val db = FirebaseFirestore.getInstance()

    private suspend fun <T> timed(block: suspend () -> T): Result<T> =
        cloudCall { withTimeout(CLOUD_TIMEOUT_MS) { block() } }

    // --- Staff ---

    fun observeStaff(): Flow<Live<List<StaffMember>>> =
        db.collection("staff").live { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                StaffRole.from(doc.getString("role"))?.let {
                    StaffMember(doc.id, doc.getString("name").orEmpty(), it)
                }
            }.sortedBy { it.name.lowercase() }
        }

    fun observeOwnRole(uid: String): Flow<Live<Maybe<StaffRole>>> =
        db.collection("staff").document(uid).live { Maybe(StaffRole.from(it.getString("role"))) }

    /** Owner only. Also removes a waiting application. */
    suspend fun appoint(uid: String, name: String, role: StaffRole): Result<Unit> = timed {
        db.runBatch { batch ->
            batch.set(
                db.collection("staff").document(uid),
                mapOf("name" to name, "role" to role.name, "appointedAt" to FieldValue.serverTimestamp())
            )
            batch.delete(db.collection("staffApplications").document(uid))
        }.await()
        Unit
    }

    suspend fun dismiss(uid: String): Result<Unit> = timed {
        db.collection("staff").document(uid).delete().await()
        Unit
    }

    suspend fun apply(uid: String, name: String, userId: String, role: StaffRole, note: String): Result<Unit> =
        timed {
            db.collection("staffApplications").document(uid).set(
                mapOf(
                    "name" to name.take(50),
                    "userId" to userId,
                    "role" to role.name,
                    "note" to note.trim().take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Unit
        }

    fun observeApplications(): Flow<Live<List<StaffApplication>>> =
        db.collection("staffApplications").limit(100).live { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                StaffRole.from(doc.getString("role"))?.let {
                    StaffApplication(
                        doc.id, doc.getString("name").orEmpty(), doc.getString("userId").orEmpty(),
                        it, doc.getString("note").orEmpty()
                    )
                }
            }
        }

    suspend fun rejectApplication(uid: String): Result<Unit> = timed {
        db.collection("staffApplications").document(uid).delete().await()
        Unit
    }

    /** A staff member can be messaged without a friend request being accepted first. */
    suspend fun openStaffContact(
        uid: String,
        staffUid: String,
        staffName: String
    ): Result<Unit> = timed {
        val myName = db.collection("users").document(uid).get().await().getString("name")
            ?: error("Profile abhi online save nahi hui")
        val members = listOf(uid, staffUid).sorted()
        val pair = db.collection("friendships").document(members.joinToString("_"))
        // Reading a missing friendship is not allowed by the rules, so the list is queried.
        val existing = db.collection("friendships").whereArrayContains("members", uid)
            .get().await().documents.firstOrNull { it.id == pair.id }
        if (existing == null) {
            pair.set(
                mapOf(
                    "members" to members,
                    "requestedBy" to uid,
                    "status" to "ACCEPTED",
                    "names" to mapOf(uid to myName, staffUid to staffName),
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
        } else if (existing.getString("status") != "ACCEPTED") {
            pair.update("status", "ACCEPTED").await()
        }
        Unit
    }

    // --- Reports and bans ---

    suspend fun sendReport(
        copUid: String,
        copName: String,
        target: UserCard,
        reason: String
    ): Result<Unit> = cloudCall {
        require(reason.trim().length >= 5) { "Report mein tafseel likhein" }
        withTimeout(CLOUD_TIMEOUT_MS) {
            db.collection("reports").document().set(
                mapOf(
                    "copUid" to copUid,
                    "copName" to copName,
                    "targetUid" to target.uid,
                    "targetName" to target.name,
                    "targetUserId" to target.userId,
                    "reason" to reason.trim().take(1000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Unit
        }
    }

    fun observeReports(): Flow<Live<List<CopReport>>> =
        db.collection("reports").orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
            .live { snapshot ->
                snapshot.documents.map { doc ->
                    CopReport(
                        doc.id,
                        doc.getString("copName").orEmpty(),
                        doc.getString("targetUid").orEmpty(),
                        doc.getString("targetName").orEmpty(),
                        doc.getString("targetUserId").orEmpty(),
                        doc.getString("reason").orEmpty(),
                        doc.timeOf("createdAt")
                    )
                }
            }

    suspend fun deleteReport(id: String): Result<Unit> = timed {
        db.collection("reports").document(id).delete().await()
        Unit
    }

    fun observeBan(uid: String): Flow<Live<ChatBan>> =
        db.collection("bans").document(uid).live {
            ChatBan(it.timeOf("until"), it.getBoolean("allChat") ?: false)
        }

    /** Moderator: ban from every group chat for two hours, after a kick in [groupId]. */
    suspend fun moderatorBan(targetUid: String, byUid: String, groupId: String): Result<Unit> = timed {
        db.collection("bans").document(targetUid).set(
            mapOf(
                "until" to Timestamp(Date(System.currentTimeMillis() + CloudGroupService.PENALTY_MS)),
                "allChat" to false,
                "by" to byUid,
                "groupId" to groupId
            )
        ).await()
        Unit
    }

    /** Owner only: ban from all chat for [durationMs]. */
    suspend fun ownerBan(targetUid: String, byUid: String, durationMs: Long): Result<Unit> = timed {
        db.collection("bans").document(targetUid).set(
            mapOf(
                "until" to Timestamp(Date(System.currentTimeMillis() + durationMs)),
                "allChat" to true,
                "by" to byUid,
                "groupId" to ""
            )
        ).await()
        Unit
    }

    suspend fun unban(targetUid: String): Result<Unit> = timed {
        db.collection("bans").document(targetUid).delete().await()
        Unit
    }

    // --- Owner messages ---

    fun observeAnnouncements(limit: Long): Flow<Live<List<Announcement>>> =
        db.collection("announcements").orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
            .live { snapshot ->
                snapshot.documents.map { doc ->
                    Announcement(
                        doc.id,
                        doc.getString("title").orEmpty(),
                        doc.getString("text").orEmpty(),
                        doc.timeOf("createdAt")
                    )
                }
            }

    suspend fun announce(title: String, text: String): Result<Unit> = cloudCall {
        require(title.isNotBlank() && text.isNotBlank()) { "Title aur message likhein" }
        withTimeout(CLOUD_TIMEOUT_MS) {
            db.collection("announcements").document().set(
                mapOf(
                    "title" to title.trim().take(80),
                    "text" to text.trim().take(1000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Unit
        }
    }

    suspend fun deleteAnnouncement(id: String): Result<Unit> = timed {
        db.collection("announcements").document(id).delete().await()
        Unit
    }

    // --- Top-ups ---

    fun observePaymentMethods(): Flow<Live<List<PaymentMethod>>> =
        db.collection("paymentMethods").live { snapshot ->
            snapshot.documents.map { doc ->
                PaymentMethod(
                    doc.id,
                    doc.getString("name").orEmpty(),
                    doc.getString("account").orEmpty(),
                    doc.getString("holder").orEmpty(),
                    doc.getString("instructions").orEmpty(),
                    doc.getLong("minCoins") ?: 1L
                )
            }.sortedBy { it.name.lowercase() }
        }

    suspend fun addPaymentMethod(method: PaymentMethod): Result<Unit> = cloudCall {
        require(method.name.isNotBlank() && method.account.isNotBlank()) {
            "Method ka naam aur account number likhein"
        }
        withTimeout(CLOUD_TIMEOUT_MS) {
            db.collection("paymentMethods").document().set(
                mapOf(
                    "name" to method.name.trim().take(60),
                    "account" to method.account.trim().take(120),
                    "holder" to method.holder.trim().take(60),
                    "instructions" to method.instructions.trim().take(300),
                    "minCoins" to method.minCoins.coerceAtLeast(1L)
                )
            ).await()
            Unit
        }
    }

    suspend fun deletePaymentMethod(id: String): Result<Unit> = timed {
        db.collection("paymentMethods").document(id).delete().await()
        Unit
    }

    suspend fun submitTopUp(
        uid: String,
        method: PaymentMethod,
        coins: Long,
        pricePkr: Long,
        reference: String
    ): Result<Unit> = cloudCall {
        require(coins >= method.minCoins) { "${method.name} par kam az kam ${method.minCoins} coins" }
        require(reference.isNotBlank()) { "Transaction ID likhein" }
        withTimeout(CLOUD_TIMEOUT_MS) {
            val name = db.collection("users").document(uid).get().await().getString("name")
                ?: error("Profile abhi online save nahi hui")
            db.collection("topups").document().set(
                mapOf(
                    "uid" to uid,
                    "name" to name,
                    "method" to method.name,
                    "coins" to coins,
                    "amountPkr" to coins * pricePkr,
                    "reference" to reference.trim().take(100),
                    "status" to "PENDING",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Unit
        }
    }

    fun observeOwnTopUps(uid: String): Flow<Live<List<TopUpOrder>>> =
        db.collection("topups").whereEqualTo("uid", uid).live { snapshot ->
            snapshot.documents.map { it.toOrder() }.sortedByDescending { it.createdAt }
        }

    fun observePendingTopUps(): Flow<Live<List<TopUpOrder>>> =
        db.collection("topups").whereEqualTo("status", "PENDING").live { snapshot ->
            snapshot.documents.map { it.toOrder() }.sortedBy { it.createdAt }
        }

    /** Owner only. Approving adds the coins and counts them for the Top 10 buyers. */
    suspend fun reviewTopUp(orderId: String, approve: Boolean): Result<Unit> = timed {
        db.runTransaction { transaction ->
            val orderRef = db.collection("topups").document(orderId)
            val order = transaction.get(orderRef)
            if (order.getString("status") == "PENDING") {
                if (approve) {
                    val uid = order.getString("uid").orEmpty()
                    val coins = order.getLong("coins") ?: 0L
                    transaction.update(
                        db.collection("wallets").document(uid), "coins", FieldValue.increment(coins)
                    )
                    transaction.set(
                        db.collection("buyers").document(uid),
                        mapOf(
                            "name" to order.getString("name").orEmpty(),
                            "totalCoins" to FieldValue.increment(coins)
                        ),
                        SetOptions.merge()
                    )
                }
                transaction.update(orderRef, "status", if (approve) "APPROVED" else "REJECTED")
            }
            true
        }.await()
        Unit
    }

    suspend fun topBuyers(): Result<List<Buyer>> = timed {
        db.collection("buyers").orderBy("totalCoins", Query.Direction.DESCENDING).limit(10)
            .get().await().documents.map {
                Buyer(it.id, it.getString("name").orEmpty(), it.getLong("totalCoins") ?: 0L)
            }
    }

    private fun DocumentSnapshot.toOrder() = TopUpOrder(
        id = id,
        uid = getString("uid").orEmpty(),
        name = getString("name").orEmpty(),
        method = getString("method").orEmpty(),
        coins = getLong("coins") ?: 0L,
        amountPkr = getLong("amountPkr") ?: 0L,
        reference = getString("reference").orEmpty(),
        status = getString("status").orEmpty(),
        createdAt = timeOf("createdAt")
    )

    // --- Daily spin ---

    fun observeSpin(uid: String): Flow<Live<Maybe<SpinStatus>>> =
        db.collection("spins").document(uid).live { doc ->
            Maybe(
                if (doc.exists()) SpinStatus(doc.timeOf("lastSpin"), doc.getBoolean("claimed") ?: false)
                else null
            )
        }

    /** Spins (or finishes an interrupted spin) and adds the reward to the wallet. */
    suspend fun spin(uid: String): Result<Int> = timed {
        val document = db.collection("spins").document(uid)
        var current = document.get(Source.SERVER).await()
        val unfinished = current.exists() && current.getBoolean("claimed") == false
        if (!unfinished) {
            val last = current.getTimestamp("lastSpin")?.toDate()?.time
            check(last == null || System.currentTimeMillis() >= last + SpinReward.COOLDOWN_MS) {
                "24 ghante baad dobara spin karein"
            }
            document.set(mapOf("lastSpin" to FieldValue.serverTimestamp(), "claimed" to false)).await()
            current = document.get(Source.SERVER).await()
        }
        val time = current.getTimestamp("lastSpin") ?: error("Spin save nahi hua")
        val slot = SpinReward.slotForSpinTime(time.seconds * 1000L + time.nanoseconds / 1_000_000)
        val reward = SpinReward.WHEEL[slot]
        db.runBatch { batch ->
            batch.update(document, "claimed", true)
            if (reward.coins > 0 || reward.stars > 0) {
                batch.update(
                    db.collection("wallets").document(uid),
                    mapOf(
                        "coins" to FieldValue.increment(reward.coins),
                        "authority" to FieldValue.increment(reward.stars)
                    )
                )
            }
        }.await()
        slot
    }

    /** Owner only. Adds (or, with negative numbers, removes) coins and authority of any user. */
    suspend fun adjustWallet(uid: String, coins: Long, authority: Long): Result<Unit> = timed {
        db.runTransaction { transaction ->
            val ref = db.collection("wallets").document(uid)
            val snapshot = transaction.get(ref)
            val newCoins = ((snapshot.getLong("coins") ?: 0L) + coins).coerceAtLeast(0L)
            val newAuthority = ((snapshot.getLong("authority") ?: 0L) + authority).coerceAtLeast(0L)
            transaction.set(
                ref,
                mapOf("coins" to newCoins, "authority" to newAuthority),
                SetOptions.merge()
            )
            true
        }.await()
        Unit
    }
}
