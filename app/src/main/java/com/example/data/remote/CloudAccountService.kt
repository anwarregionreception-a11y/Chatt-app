package com.example.data.remote

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeout

data class UserIdRecord(val uid: String, val loginEmail: String?)

data class Wallet(val coins: Long, val authority: Long)

/** Everything the user controls about their profile. Only the user can read it. */
data class PrivateSettings(
    val name: String,
    val country: String = "",
    val mobile: String = "",
    val contactEmail: String = "",
    val showMobile: Boolean = false,
    val showEmail: Boolean = false,
    val showCoins: Boolean = true,
    val allowRequests: Boolean = true
)

/** The profile other users see. Hidden values are simply not stored in it. */
data class UserCard(
    val uid: String,
    val name: String,
    val userId: String,
    val country: String,
    val mobile: String?,
    val email: String?,
    val coins: Long?,
    val authority: Long,
    val createdAt: Long
)

data class Activity(val groupId: String, val groupName: String, val at: Long)

/**
 * Accounts, User IDs, wallets and profiles.
 *
 * Firestore layout (see firestore.rules):
 *   userids/{id}     -> uid, optional loginEmail
 *   owned/{uid}      -> the User ID of an account
 *   private/{uid}    -> settings
 *   wallets/{uid}    -> coins and authority
 *   users/{uid}      -> public profile
 *   activity/{uid}   -> public group the user is in now
 */
class CloudAccountService {
    private val db = FirebaseFirestore.getInstance()
    private val users = db.collection("users")

    private fun wallet(uid: String) = db.collection("wallets").document(uid)

    // --- User IDs ---

    suspend fun findUserId(id: String): Result<UserIdRecord?> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            val doc = db.collection("userids").document(id).get().await()
            val uid = doc.getString("uid")
            if (uid == null) null else UserIdRecord(uid, doc.getString("loginEmail"))
        }
    }

    suspend fun claimUserId(uid: String, id: String, loginEmail: String?): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            val target = db.collection("userids").document(id)
            if (target.get().await().exists()) error("User ID already taken")
            val record = mutableMapOf<String, Any>("uid" to uid)
            if (loginEmail != null) record["loginEmail"] = loginEmail
            db.runBatch { batch ->
                batch.set(target, record)
                batch.set(db.collection("owned").document(uid), mapOf("userId" to id))
            }.await()
            Unit
        }
    }

    /** A null [loginEmail] turns off "login with User ID" for an email account. */
    suspend fun setLoginEmail(uid: String, id: String, loginEmail: String?): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            val record = mutableMapOf<String, Any>("uid" to uid)
            if (loginEmail != null) record["loginEmail"] = loginEmail
            db.collection("userids").document(id).set(record).await()
            Unit
        }
    }

    fun observeUserIdRecord(id: String): Flow<Live<UserIdRecord>> =
        db.collection("userids").document(id).live {
            UserIdRecord(it.getString("uid").orEmpty(), it.getString("loginEmail"))
        }

    /** Emits the User ID of the account, or "" when none has been chosen. */
    fun observeOwnUserId(uid: String): Flow<Live<String>> = callbackFlow {
        val document = db.collection("owned").document(uid)
        val registration = document.addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
            if (error != null) {
                trySend(Live<String>(error = readableCloudError(error)))
            } else if (doc != null && (doc.exists() || !doc.metadata.isFromCache)) {
                // An empty cache must not be mistaken for "no User ID".
                trySend(Live(doc.getString("userId").orEmpty()))
            }
        }
        awaitClose { registration.remove() }
    }

    // --- Settings ---

    /** Creates the settings on first use, taking over values from an older public profile. */
    suspend fun ensureSettings(uid: String, name: String, email: String): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            val document = db.collection("private").document(uid)
            if (!document.get().await().exists()) {
                val old = users.document(uid).get().await()
                val settings = PrivateSettings(
                    name = old.getString("name") ?: name,
                    contactEmail = email,
                    showCoins = old.getBoolean("showCoins") ?: true
                )
                document.set(settings.toMap()).await()
            }
            Unit
        }
    }

    fun observeSettings(uid: String): Flow<Live<PrivateSettings>> = callbackFlow {
        val registration = db.collection("private").document(uid).addSnapshotListener { doc, error ->
            if (error != null) {
                trySend(Live<PrivateSettings>(error = readableCloudError(error)))
            } else if (doc != null) {
                val name = doc.getString("name")
                if (name != null) {
                    trySend(
                        Live(
                            PrivateSettings(
                                name = name,
                                country = doc.getString("country").orEmpty(),
                                mobile = doc.getString("mobile").orEmpty(),
                                contactEmail = doc.getString("contactEmail").orEmpty(),
                                showMobile = doc.getBoolean("showMobile") ?: false,
                                showEmail = doc.getBoolean("showEmail") ?: false,
                                showCoins = doc.getBoolean("showCoins") ?: true,
                                allowRequests = doc.getBoolean("allowRequests") ?: true
                            )
                        )
                    )
                }
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveSettings(uid: String, settings: PrivateSettings): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            db.collection("private").document(uid).set(settings.toMap()).await()
            Unit
        }
    }

    private fun PrivateSettings.toMap(): Map<String, Any> = mapOf(
        "name" to name.take(50),
        "country" to country,
        "mobile" to mobile.take(20),
        "contactEmail" to contactEmail.take(100),
        "showMobile" to showMobile,
        "showEmail" to showEmail,
        "showCoins" to showCoins,
        "allowRequests" to allowRequests
    )

    // --- Wallet ---

    suspend fun ensureWallet(uid: String): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            if (!wallet(uid).get().await().exists()) {
                wallet(uid).set(mapOf("coins" to 50L, "authority" to 1L)).await()
            }
            Unit
        }
    }

    /** Only server-confirmed balances are emitted. */
    fun observeWallet(uid: String): Flow<Live<Wallet>> = callbackFlow {
        val registration = wallet(uid).addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
            if (error != null) {
                trySend(Live<Wallet>(error = readableCloudError(error)))
            } else if (doc != null && doc.exists() &&
                !doc.metadata.isFromCache && !doc.metadata.hasPendingWrites()
            ) {
                trySend(Live(Wallet(doc.getLong("coins") ?: 0L, doc.getLong("authority") ?: 0L)))
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun investAuthority(uid: String, amount: Long): Result<Unit> = cloudCall {
        require(amount > 0) { "Coins ki tadaad likhein" }
        withTimeout(CLOUD_TIMEOUT_MS) {
            db.runTransaction { transaction ->
                val doc = transaction.get(wallet(uid))
                val coins = doc.getLong("coins") ?: 0L
                if (coins >= amount) {
                    transaction.update(
                        wallet(uid),
                        mapOf(
                            "coins" to coins - amount,
                            "authority" to (doc.getLong("authority") ?: 0L) + amount
                        )
                    )
                }
                coins >= amount
            }.await().also { enough -> check(enough) { "Coins kam hain" } }
            Unit
        }
    }

    suspend fun claimAdReward(uid: String): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            val last = wallet(uid).get().await().getTimestamp("lastAdAt")?.toDate()?.time
            if (last != null) {
                val wait = last + AD_COOLDOWN_MS - System.currentTimeMillis()
                check(wait <= 0) { "Agla ad reward ${wait / 60_000 + 1} minute baad milega" }
            }
            wallet(uid).update(
                mapOf("coins" to FieldValue.increment(1), "lastAdAt" to FieldValue.serverTimestamp())
            ).await()
            Unit
        }
    }

    /** Owner only. */
    suspend fun setWallet(uid: String, coins: Long, authority: Long): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            wallet(uid).set(mapOf("coins" to coins, "authority" to authority)).await()
            Unit
        }
    }

    // --- Public profiles ---

    /** Writes the public profile. Writes only when something changed. */
    suspend fun publishCard(
        uid: String,
        loginEmail: String?,
        settings: PrivateSettings,
        wallet: Wallet,
        userId: String,
        registeredAt: Long,
        keywords: List<String>
    ): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            val document = users.document(uid)
            val current = document.get().await()
            val name = settings.name.take(50).ifBlank { "User" }
            val data = mutableMapOf<String, Any>(
                "name" to name,
                "nameLower" to name.lowercase().take(50),
                "keywords" to keywords,
                "userId" to userId,
                "country" to settings.country,
                "showCoins" to settings.showCoins,
                "allowRequests" to settings.allowRequests,
                "authorityCoins" to wallet.authority,
                "createdAt" to registeredAt
            )
            if (settings.showCoins) data["coins"] = wallet.coins
            if (settings.showMobile && settings.mobile.isNotBlank()) data["mobile"] = settings.mobile.take(20)
            if (settings.showEmail && settings.contactEmail.isNotBlank()) {
                data["email"] = settings.contactEmail.take(100)
            }
            if (current.data != data) {
                val batch = db.batch()
                batch.set(document, data)
                if (!current.exists() && loginEmail != null) {
                    batch.set(
                        db.collection("emails").document(loginEmail.lowercase()),
                        mapOf("uid" to uid)
                    )
                }
                batch.commit().await()
            }
            Unit
        }
    }

    fun observeCard(uid: String): Flow<Live<UserCard>> = callbackFlow {
        val registration = users.document(uid).addSnapshotListener { doc, error ->
            if (error != null) {
                trySend(Live<UserCard>(error = readableCloudError(error)))
            } else if (doc != null) {
                val card = doc.toCard()
                if (card != null) trySend(Live(card))
                else if (!doc.metadata.isFromCache) trySend(Live<UserCard>(error = "Profile nahi mili"))
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun loadCard(uid: String): Result<UserCard> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            users.document(uid).get().await().toCard() ?: error("Profile nahi mili")
        }
    }

    /** All users, or the users of one country. */
    suspend fun listUsers(country: String, limit: Long): Result<List<UserCard>> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            // Sorting a filtered list on the server would need an extra index, so it is done here.
            val query = if (country.isBlank()) users.orderBy("nameLower")
            else users.whereEqualTo("country", country)
            query.limit(limit).get().await().documents
                .mapNotNull { it.toCard() }
                .sortedBy { it.name.lowercase() }
        }
    }

    suspend fun countUsers(country: String): Result<Long> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            val query: Query = if (country.isBlank()) users else users.whereEqualTo("country", country)
            query.count().get(AggregateSource.SERVER).await().count
        }
    }

    suspend fun topAuthority(): Result<List<UserCard>> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            users.orderBy("authorityCoins", Query.Direction.DESCENDING).limit(10)
                .get().await().documents.mapNotNull { it.toCard() }
        }
    }

    private fun DocumentSnapshot.toCard(): UserCard? {
        val name = getString("name") ?: return null
        return UserCard(
            uid = id,
            name = name,
            userId = getString("userId").orEmpty(),
            country = getString("country").orEmpty(),
            mobile = getString("mobile"),
            email = getString("email"),
            coins = if (getBoolean("showCoins") == true) getLong("coins") else null,
            authority = getLong("authorityCoins") ?: 0L,
            createdAt = getLong("createdAt") ?: 0L
        )
    }

    // --- Current group ---

    suspend fun setActivity(uid: String, groupId: String, groupName: String): Result<Unit> = cloudCall {
        withTimeout(CLOUD_TIMEOUT_MS) {
            db.collection("activity").document(uid).set(
                mapOf("groupId" to groupId, "groupName" to groupName, "at" to FieldValue.serverTimestamp())
            ).await()
            Unit
        }
    }

    fun clearActivity(uid: String) {
        db.collection("activity").document(uid).delete()
    }

    fun observeActivity(uid: String): Flow<Live<Activity>> =
        db.collection("activity").document(uid).live { doc ->
            Activity(
                doc.getString("groupId").orEmpty(),
                doc.getString("groupName").orEmpty(),
                doc.timeOf("at")
            )
        }

    companion object {
        const val AD_COOLDOWN_MS = 60L * 60L * 1000L
        const val ACTIVITY_WINDOW_MS = 11L * 60L * 1000L
    }
}
