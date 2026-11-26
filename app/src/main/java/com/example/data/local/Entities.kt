package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

object UserRoles {
    const val OWNER = "OWNER"
    const val DEVELOPER = "DEVELOPER"
    const val USER = "USER"
}

object AppConstants {
    const val OWNER_ID = "owner_001"
    const val COIN_PRICE_PKR = 50L
    const val GROUP_PURCHASE_COINS = 200L
    const val GROUP_INITIAL_MEMBER_LIMIT = 50
    const val GROUP_LIFE_DAYS = 30L
    const val GROUP_RENEWAL_PROMPT_DAY = 25L
    const val MIN_GROUP_FUND_INITIAL = 100L
    const val MIN_GROUP_FUND_RENEWAL_USE = 50L
    const val CRYPTO_MIN_COINS = 20L
    const val GLORIOUS_BYPASS_COINS = 50L
    const val TEMP_PASS_USER_EXPIRY_MS = 15L * 60L * 1000L
    const val TEMP_PASS_ADMIN_EXPIRY_MS = 30L * 60L * 1000L
    const val LOCKOUT_DURATION_MS = 5L * 60L * 1000L
    const val MAX_SUPERVISORS_PER_GROUP = 5
}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    @ColumnInfo(name = "mobile_number") val mobileNumber: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    @ColumnInfo(name = "temp_password_hash") val tempPasswordHash: String? = null,
    @ColumnInfo(name = "temp_password_expiry") val tempPasswordExpiry: Long? = null,
    @ColumnInfo(name = "temp_password_used") val tempPasswordUsed: Boolean = false,
    @ColumnInfo(name = "is_temp_login") val isTempLogin: Boolean = false,
    @ColumnInfo(name = "failed_attempts") val failedAttempts: Int = 0,
    @ColumnInfo(name = "is_blocked") val isBlocked: Boolean = false,
    @ColumnInfo(name = "blocked_until") val blockedUntil: Long? = null,
    val role: String = UserRoles.USER,
    @ColumnInfo(name = "developer_level") val developerLevel: Int = 0,
    @ColumnInfo(name = "dev_can_reset_password") val devCanResetPassword: Boolean = false,
    @ColumnInfo(name = "dev_can_block_users") val devCanBlockUsers: Boolean = false,
    @ColumnInfo(name = "dev_can_approve_coins") val devCanApproveCoins: Boolean = false,
    @ColumnInfo(name = "dev_can_moderate_groups") val devCanModerateGroups: Boolean = false,
    @ColumnInfo(name = "wallet_coins") val walletCoins: Long = 0L,
    @ColumnInfo(name = "authority_coins") val authorityCoins: Long = 0L,
    @ColumnInfo(name = "show_coins_on_profile") val showCoinsOnProfile: Boolean = true,
    @ColumnInfo(name = "receive_friend_requests") val receiveFriendRequests: Boolean = true,
    @ColumnInfo(name = "receive_group_invites") val receiveGroupInvites: Boolean = true,
    @ColumnInfo(name = "profile_image_uri") val profileImageUri: String? = null,
    @ColumnInfo(name = "last_kick_at") val lastKickAt: Long = 0L,
    @ColumnInfo(name = "unfair_kick_count") val unfairKickCount: Int = 0,
    @ColumnInfo(name = "last_owner_ban_at") val lastOwnerBanAt: Long = 0L,
    @ColumnInfo(name = "ads_watched_count") val adsWatchedCount: Int = 0
)

@Entity(tableName = "friend_relations")
data class FriendRelationEntity(
    @PrimaryKey val id: String,
    val userA: String,
    val userB: String,
    val status: String, // "PENDING", "ACCEPTED"
    val requestedBy: String,
    val createdAt: Long
)

@Entity(tableName = "direct_messages")
data class DirectMessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val receiverId: String,
    val text: String,
    val timestamp: Long
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val ownerId: String,
    val isPublic: Boolean = true,
    val memberLimit: Int = AppConstants.GROUP_INITIAL_MEMBER_LIMIT,
    val createdAt: Long,
    val expiresAt: Long,
    val currentFund: Long = 0L,
    val totalFundUsed: Long = 0L,
    val supervisorCanAddMembers: Boolean = true,
    val supervisorCanRemoveMembers: Boolean = true,
    val supervisorCanBlacklist: Boolean = false,
    val supervisorCanTogglePrivacy: Boolean = false,
    val ownerChatBannedUntil: Long = 0L,
    val renewalCount: Int = 0
)

@Entity(tableName = "group_members")
data class GroupMemberEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val userId: String,
    val isSupervisor: Boolean = false,
    val isBlacklisted: Boolean = false,
    val kickedUntil: Long = 0L,
    val kickedByUserId: String? = null,
    val kickReason: String? = null,
    val paidBypassUntil: Long = 0L,
    val joinedAt: Long
)

@Entity(tableName = "group_messages")
data class GroupMessageEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val senderId: String,
    val senderName: String,
    val taggedUserNames: String = "",
    val text: String,
    val timestamp: Long
)

@Entity(tableName = "group_donations")
data class GroupDonationEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val donorId: String,
    val donorName: String,
    val amount: Long,
    val seatsAdded: Int,
    val timestamp: Long
)

@Entity(tableName = "coin_orders")
data class CoinOrderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val userName: String,
    val paymentMethod: String,
    val coinsRequested: Long,
    val amountPkr: Long,
    val referenceNote: String,
    val status: String, // "PENDING", "APPROVED", "REJECTED"
    val reviewedBy: String? = null,
    val timestamp: Long
)

@Entity(tableName = "system_complaints")
data class SystemComplaintEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val groupName: String,
    val groupOwnerId: String,
    val kickerId: String,
    val kickerName: String,
    val kickedUserId: String,
    val kickedUserName: String,
    val chatLogTenLines: String,
    val status: String, // "PENDING", "PUNISHED", "IGNORED"
    val timestamp: Long
)

@Entity(tableName = "app_ratings")
data class AppRatingEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val userName: String,
    val stars: Int,
    val comment: String,
    val timestamp: Long
)
