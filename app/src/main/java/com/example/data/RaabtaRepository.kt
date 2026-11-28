package com.example.data

import com.example.data.local.*
import com.example.domain.AuthorityEngine
import com.example.security.PasswordHasher
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class LoginResult {
    data class Success(val user: UserEntity) : LoginResult()
    data class ForceChangePassword(val user: UserEntity, val prefilledTemp: String) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

class RaabtaRepository(private val dao: AppDao) {

    // --- ONLINE ACCOUNTS (local copy) ---

    suspend fun saveSignedInUser(userId: String, name: String, email: String) {
        val existing = dao.getUserById(userId)
        dao.upsertUser(
            existing?.copy(name = name, email = email) ?: UserEntity(
                id = userId,
                name = name,
                email = email,
                mobileNumber = "",
                passwordHash = "", // Firebase Authentication keeps the password
                walletCoins = 50L,
                authorityCoins = 1L
            )
        )
    }

    suspend fun savePeer(userId: String, name: String, email: String): UserEntity {
        val existing = dao.getUserById(userId)
        val peer = existing?.copy(name = name, email = email.ifBlank { existing.email })
            ?: UserEntity(
                id = userId,
                name = name,
                email = email,
                mobileNumber = "",
                passwordHash = "",
                showCoinsOnProfile = false
            )
        dao.upsertUser(peer)
        return peer
    }

    fun observeAllUsers(): Flow<List<UserEntity>> = dao.observeAllUsers()
    fun observeUser(userId: String): Flow<UserEntity?> = dao.observeUser(userId)
    fun observeFriendRelations(userId: String): Flow<List<FriendRelationEntity>> = dao.observeFriendRelations(userId)
    fun observeUserDirectMessages(userId: String): Flow<List<DirectMessageEntity>> = dao.observeUserDirectMessages(userId)
    fun observeConversation(u1: String, u2: String): Flow<List<DirectMessageEntity>> = dao.observeConversation(u1, u2)
    fun observeAllGroups(): Flow<List<GroupEntity>> = dao.observeAllGroups()
    fun observeGroup(groupId: String): Flow<GroupEntity?> = dao.observeGroup(groupId)
    fun observeAllGroupMembers(): Flow<List<GroupMemberEntity>> = dao.observeAllGroupMembers()
    fun observeGroupMembers(groupId: String): Flow<List<GroupMemberEntity>> = dao.observeGroupMembers(groupId)
    fun observeGroupMessages(groupId: String): Flow<List<GroupMessageEntity>> = dao.observeGroupMessages(groupId)
    fun observeGroupDonations(groupId: String): Flow<List<GroupDonationEntity>> = dao.observeGroupDonations(groupId)
    fun observeAllCoinOrders(): Flow<List<CoinOrderEntity>> = dao.observeAllCoinOrders()
    fun observeUserCoinOrders(userId: String): Flow<List<CoinOrderEntity>> = dao.observeUserCoinOrders(userId)
    fun observeOwnerComplaints(ownerId: String): Flow<List<SystemComplaintEntity>> = dao.observeOwnerComplaints(ownerId)
    fun observeAllRatings(): Flow<List<AppRatingEntity>> = dao.observeAllRatings()

    suspend fun getUserById(userId: String): UserEntity? = dao.getUserById(userId)

    // --- AUTHENTICATION & PASSWORD RESET ---

    suspend fun registerUser(
        name: String,
        email: String,
        mobile: String,
        password: String
    ): Result<UserEntity> {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        val cleanMobile = mobile.trim()
        if (cleanName.isBlank() || cleanEmail.isBlank() || cleanMobile.isBlank()) {
            return Result.failure(IllegalArgumentException("Name, email, and mobile number are required"))
        }
        PasswordHasher.validateNewPassword(password)?.let {
            return Result.failure(IllegalArgumentException(it))
        }
        if (dao.findUserByEmailOrMobile(cleanEmail) != null || dao.findUserByEmailOrMobile(cleanMobile) != null) {
            return Result.failure(IllegalArgumentException("Account with this email or mobile already exists"))
        }
        val user = UserEntity(
            id = "usr_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            email = cleanEmail,
            mobileNumber = cleanMobile,
            passwordHash = PasswordHasher.hashPassword(password),
            role = UserRoles.USER,
            walletCoins = 50L,
            authorityCoins = 1L, // Level 1 New Comer
            showCoinsOnProfile = true
        )
        dao.upsertUser(user)
        return Result.success(user)
    }

    suspend fun login(identifier: String, rawPassword: String): LoginResult {
        val cleanId = identifier.trim()
        if (cleanId.isBlank() || rawPassword.isBlank()) {
            return LoginResult.Error("Please enter email/mobile and password")
        }
        var user = dao.findUserByEmailOrMobile(cleanId)
            ?: return LoginResult.Error("Account not found")

        val now = System.currentTimeMillis()
        if (user.isBlocked) {
            val until = user.blockedUntil
            if (until == null) {
                return LoginResult.Error("Your account has been blocked by the Owner/Admin.")
            }
            if (now < until) {
                val secs = ((until - now) / 1000L).coerceAtLeast(1L)
                val mins = secs / 60L
                val remSec = secs % 60L
                return LoginResult.Error("Account 5 min ke liye block hai. Try again in ${mins}m ${remSec}s.")
            } else {
                user = user.copy(isBlocked = false, blockedUntil = null, failedAttempts = 0)
                dao.updateUser(user)
            }
        }

        // 1. Check regular password
        if (PasswordHasher.verifyPassword(rawPassword, user.passwordHash)) {
            val updated = user.copy(failedAttempts = 0)
            dao.updateUser(updated)
            return if (updated.isTempLogin) {
                LoginResult.ForceChangePassword(updated, "")
            } else {
                LoginResult.Success(updated)
            }
        }

        // 2. Check temporary password
        if (user.tempPasswordHash != null) {
            if (user.tempPasswordUsed) {
                return LoginResult.Error("Temporary Password expire ho gaya hai, dobara Reset karein")
            }
            val expiry = user.tempPasswordExpiry ?: 0L
            if (now > expiry) {
                return LoginResult.Error("Temporary Password expire ho gaya hai, dobara Reset karein")
            }
            if (PasswordHasher.verifyPassword(rawPassword, user.tempPasswordHash)) {
                val updated = user.copy(
                    tempPasswordUsed = true,
                    isTempLogin = true,
                    failedAttempts = 0
                )
                dao.updateUser(updated)
                return LoginResult.ForceChangePassword(updated, rawPassword)
            }
        }

        // 3. Failed attempt tracking (3 strikes -> 5 min block)
        val newAttempts = user.failedAttempts + 1
        return if (newAttempts >= 3) {
            dao.updateUser(
                user.copy(
                    failedAttempts = 0,
                    isBlocked = true,
                    blockedUntil = now + AppConstants.LOCKOUT_DURATION_MS
                )
            )
            LoginResult.Error("3 ghalat attempts! Account 5 minutes ke liye block ho gaya hai.")
        } else {
            dao.updateUser(user.copy(failedAttempts = newAttempts))
            LoginResult.Error("Invalid password. ${3 - newAttempts} attempt(s) remaining.")
        }
    }

    suspend fun requestUserForgotPassword(identifier: String): Result<Pair<UserEntity, String>> {
        val user = dao.findUserByEmailOrMobile(identifier.trim())
            ?: return Result.failure(IllegalArgumentException("Email ya Mobile Number database me maujood nahi hai"))
        val tempPass = PasswordHasher.generateTemporaryPassword()
        val now = System.currentTimeMillis()
        // Note: Asal passwordHash is NOT touched!
        val updated = user.copy(
            tempPasswordHash = PasswordHasher.hashPassword(tempPass),
            tempPasswordExpiry = now + AppConstants.TEMP_PASS_USER_EXPIRY_MS,
            tempPasswordUsed = false,
            failedAttempts = 0
        )
        dao.updateUser(updated)
        return Result.success(updated to tempPass)
    }

    suspend fun adminResetPassword(
        actorId: String,
        targetUserId: String,
        customTempPassword: String?
    ): Result<String> {
        val actor = dao.getUserById(actorId)
            ?: return Result.failure(IllegalStateException("Admin not found"))
        if (actor.role != UserRoles.OWNER && !(actor.role == UserRoles.DEVELOPER && actor.devCanResetPassword)) {
            return Result.failure(IllegalAccessException("Permission denied"))
        }
        val target = dao.getUserById(targetUserId)
            ?: return Result.failure(IllegalArgumentException("Target user not found"))

        val tempPass = if (customTempPassword.isNullOrBlank()) {
            PasswordHasher.generateTemporaryPassword()
        } else {
            customTempPassword.trim()
        }
        if (tempPass.length < 4) {
            return Result.failure(IllegalArgumentException("Temporary password must be at least 4 characters"))
        }

        val now = System.currentTimeMillis()
        // Admin reset sets 30 minutes expiry; never alters original passwordHash
        val updated = target.copy(
            tempPasswordHash = PasswordHasher.hashPassword(tempPass),
            tempPasswordExpiry = now + AppConstants.TEMP_PASS_ADMIN_EXPIRY_MS,
            tempPasswordUsed = false,
            failedAttempts = 0,
            isBlocked = false,
            blockedUntil = null
        )
        dao.updateUser(updated)
        return Result.success(tempPass)
    }

    suspend fun completeForceChangePassword(
        userId: String,
        tempPasswordInput: String,
        newPassword: String,
        confirmPassword: String
    ): Result<UserEntity> {
        val user = dao.getUserById(userId)
            ?: return Result.failure(IllegalStateException("User not found"))

        if (user.tempPasswordHash != null) {
            val now = System.currentTimeMillis()
            val expiry = user.tempPasswordExpiry ?: 0L
            if (now > expiry) {
                return Result.failure(IllegalStateException("Temporary Password expire ho gaya hai, dobara Reset karein"))
            }
            if (!PasswordHasher.verifyPassword(tempPasswordInput.trim(), user.tempPasswordHash)) {
                return Result.failure(IllegalArgumentException("Temporary Password ghalat hai"))
            }
        }

        if (newPassword != confirmPassword) {
            return Result.failure(IllegalArgumentException("Password match nahi ho raha"))
        }
        PasswordHasher.validateNewPassword(newPassword)?.let {
            return Result.failure(IllegalArgumentException(it))
        }

        val updated = user.copy(
            passwordHash = PasswordHasher.hashPassword(newPassword),
            tempPasswordHash = null,
            tempPasswordExpiry = null,
            tempPasswordUsed = false,
            isTempLogin = false,
            failedAttempts = 0
        )
        dao.updateUser(updated)
        return Result.success(updated)
    }

    suspend fun changeOwnPassword(
        userId: String,
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): Result<Unit> {
        val user = dao.getUserById(userId) ?: return Result.failure(IllegalStateException("User not found"))
        if (!PasswordHasher.verifyPassword(currentPassword, user.passwordHash)) {
            return Result.failure(IllegalArgumentException("Current password is incorrect"))
        }
        if (newPassword != confirmPassword) {
            return Result.failure(IllegalArgumentException("Password match nahi ho raha"))
        }
        PasswordHasher.validateNewPassword(newPassword)?.let {
            return Result.failure(IllegalArgumentException(it))
        }
        dao.updateUser(user.copy(passwordHash = PasswordHasher.hashPassword(newPassword)))
        return Result.success(Unit)
    }

    // --- SETTINGS & PROFILE ---

    suspend fun updateSettings(
        userId: String,
        mobile: String,
        email: String,
        showCoinsOnProfile: Boolean,
        receiveFriendRequests: Boolean,
        receiveGroupInvites: Boolean,
        profileImageUri: String?
    ): Result<Unit> {
        val user = dao.getUserById(userId) ?: return Result.failure(IllegalStateException("User not found"))
        dao.updateUser(
            user.copy(
                mobileNumber = mobile.trim().ifBlank { user.mobileNumber },
                showCoinsOnProfile = showCoinsOnProfile,
                receiveFriendRequests = receiveFriendRequests,
                receiveGroupInvites = receiveGroupInvites,
                profileImageUri = profileImageUri
            )
        )
        return Result.success(Unit)
    }

    // --- OWNER & DEVELOPER MANAGEMENT ---

    suspend fun setUserBlocked(actorId: String, targetUserId: String, block: Boolean): Result<Unit> {
        val actor = dao.getUserById(actorId) ?: return Result.failure(IllegalStateException("Actor missing"))
        val target = dao.getUserById(targetUserId) ?: return Result.failure(IllegalStateException("User missing"))
        if (target.id == AppConstants.OWNER_ID || target.role == UserRoles.OWNER) {
            return Result.failure(IllegalAccessException("Owner cannot be blocked, banned, or modified"))
        }
        if (actor.role != UserRoles.OWNER && !(actor.role == UserRoles.DEVELOPER && actor.devCanBlockUsers)) {
            return Result.failure(IllegalAccessException("Permission denied"))
        }
        if (actor.role == UserRoles.DEVELOPER && target.role == UserRoles.DEVELOPER && target.developerLevel >= actor.developerLevel) {
            return Result.failure(IllegalAccessException("Cannot block same or higher level Developer"))
        }
        dao.updateUser(target.copy(isBlocked = block, blockedUntil = null, failedAttempts = 0))
        return Result.success(Unit)
    }

    suspend fun deleteUserByOwner(actorId: String, targetUserId: String): Result<Unit> {
        val actor = dao.getUserById(actorId) ?: return Result.failure(IllegalStateException("Actor missing"))
        if (actor.role != UserRoles.OWNER) {
            return Result.failure(IllegalAccessException("Only Owner can delete accounts"))
        }
        if (targetUserId == AppConstants.OWNER_ID) {
            return Result.failure(IllegalAccessException("Owner account cannot be deleted"))
        }
        dao.deleteUser(targetUserId)
        return Result.success(Unit)
    }

    suspend fun assignDeveloperRole(
        actorId: String,
        targetUserId: String,
        isDeveloper: Boolean,
        developerLevel: Int,
        canResetPassword: Boolean,
        canBlockUsers: Boolean,
        canApproveCoins: Boolean,
        canModerateGroups: Boolean
    ): Result<Unit> {
        val actor = dao.getUserById(actorId) ?: return Result.failure(IllegalStateException("Actor missing"))
        if (actor.role != UserRoles.OWNER) {
            return Result.failure(IllegalAccessException("Only Owner can assign Developer levels"))
        }
        val target = dao.getUserById(targetUserId) ?: return Result.failure(IllegalStateException("User missing"))
        if (target.id == AppConstants.OWNER_ID || target.role == UserRoles.OWNER) {
            return Result.failure(IllegalAccessException("Owner role cannot be changed"))
        }
        val updated = if (isDeveloper) {
            target.copy(
                role = UserRoles.DEVELOPER,
                developerLevel = developerLevel.coerceAtLeast(1),
                devCanResetPassword = canResetPassword,
                devCanBlockUsers = canBlockUsers,
                devCanApproveCoins = canApproveCoins,
                devCanModerateGroups = canModerateGroups
            )
        } else {
            target.copy(
                role = UserRoles.USER,
                developerLevel = 0,
                devCanResetPassword = false,
                devCanBlockUsers = false,
                devCanApproveCoins = false,
                devCanModerateGroups = false
            )
        }
        dao.updateUser(updated)
        return Result.success(Unit)
    }

    suspend fun editUserDetailsByOwner(
        actorId: String,
        targetUserId: String,
        newName: String,
        newWalletCoins: Long
    ): Result<Unit> {
        val actor = dao.getUserById(actorId) ?: return Result.failure(IllegalStateException("Actor missing"))
        if (actor.role != UserRoles.OWNER) {
            return Result.failure(IllegalAccessException("Only Owner can edit user profiles"))
        }
        val target = dao.getUserById(targetUserId) ?: return Result.failure(IllegalStateException("User missing"))
        dao.updateUser(
            target.copy(
                name = newName.trim().ifBlank { target.name },
                walletCoins = newWalletCoins.coerceAtLeast(0L)
            )
        )
        return Result.success(Unit)
    }

    // --- COINS, ADS & AUTHORITY ---

    suspend fun watchRewardedAd(userId: String): Result<Long> {
        val user = dao.getUserById(userId) ?: return Result.failure(IllegalStateException("User not found"))
        val updated = user.copy(
            walletCoins = user.walletCoins + 1L,
            adsWatchedCount = user.adsWatchedCount + 1
        )
        dao.updateUser(updated)
        return Result.success(updated.walletCoins)
    }

    suspend fun submitCoinOrder(
        userId: String,
        paymentMethod: String,
        coinsRequested: Long,
        referenceNote: String
    ): Result<Unit> {
        val user = dao.getUserById(userId) ?: return Result.failure(IllegalStateException("User not found"))
        if (coinsRequested <= 0L) {
            return Result.failure(IllegalArgumentException("Enter valid coin quantity"))
        }
        val isCrypto = paymentMethod in listOf("BTC", "USDT", "Ethereum")
        if (isCrypto && coinsRequested < AppConstants.CRYPTO_MIN_COINS) {
            return Result.failure(IllegalArgumentException("Crypto minimum purchase is 20 coins"))
        }
        val order = CoinOrderEntity(
            id = "ord_${UUID.randomUUID().toString().take(8)}",
            userId = user.id,
            userName = user.name,
            paymentMethod = paymentMethod,
            coinsRequested = coinsRequested,
            amountPkr = coinsRequested * AppConstants.COIN_PRICE_PKR,
            referenceNote = referenceNote.trim().ifBlank { "$paymentMethod-REF-${System.currentTimeMillis() % 10000}" },
            status = "PENDING",
            timestamp = System.currentTimeMillis()
        )
        dao.upsertCoinOrder(order)
        return Result.success(Unit)
    }

    suspend fun reviewCoinOrder(actorId: String, orderId: String, approve: Boolean): Result<Unit> {
        val actor = dao.getUserById(actorId) ?: return Result.failure(IllegalStateException("Actor missing"))
        if (actor.role != UserRoles.OWNER && !(actor.role == UserRoles.DEVELOPER && actor.devCanApproveCoins)) {
            return Result.failure(IllegalAccessException("Owner approval required"))
        }
        val order = dao.getCoinOrderById(orderId) ?: return Result.failure(IllegalStateException("Order not found"))
        if (order.status != "PENDING") {
            return Result.failure(IllegalStateException("Order already reviewed"))
        }
        if (approve) {
            val buyer = dao.getUserById(order.userId)
            if (buyer != null) {
                dao.updateUser(buyer.copy(walletCoins = buyer.walletCoins + order.coinsRequested))
            }
        }
        dao.upsertCoinOrder(
            order.copy(
                status = if (approve) "APPROVED" else "REJECTED",
                reviewedBy = actor.name
            )
        )
        return Result.success(Unit)
    }

    suspend fun investCoinsIntoAuthority(userId: String, amount: Long): Result<Int> {
        val user = dao.getUserById(userId) ?: return Result.failure(IllegalStateException("User not found"))
        if (amount <= 0L) return Result.failure(IllegalArgumentException("Amount must be positive"))
        if (user.walletCoins < amount) {
            return Result.failure(IllegalArgumentException("Insufficient wallet coins"))
        }
        val newAuthorityCoins = user.authorityCoins + amount
        dao.updateUser(
            user.copy(
                walletCoins = user.walletCoins - amount,
                authorityCoins = newAuthorityCoins
            )
        )
        return Result.success(AuthorityEngine.getLevel(newAuthorityCoins))
    }

    // --- FRIENDS & DIRECT CHATS ---

    suspend fun sendFriendRequest(fromUserId: String, toUserId: String): Result<Unit> {
        // A request saved only on this phone would never reach the other person.
        return Result.failure(IllegalStateException("Online friend request agle update mein aayegi"))
    }

    suspend fun respondFriendRequest(relationId: String, accept: Boolean, allRelations: List<FriendRelationEntity>): Result<Unit> {
        val rel = allRelations.firstOrNull { it.id == relationId }
            ?: return Result.failure(IllegalStateException("Request not found"))
        if (accept) {
            dao.upsertFriendRelation(rel.copy(status = "ACCEPTED"))
        } else {
            dao.deleteFriendRelation(rel.id)
        }
        return Result.success(Unit)
    }

    suspend fun sendDirectMessage(senderId: String, receiverId: String, text: String): Result<Unit> {
        val clean = text.trim()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Empty message"))
        dao.insertDirectMessage(
            DirectMessageEntity(
                id = "dm_${UUID.randomUUID().toString().take(8)}",
                senderId = senderId,
                receiverId = receiverId,
                text = clean,
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    // --- GROUPS, FUNDS, SUPERVISORS & AUTHORITY MODERATION ---

    suspend fun buyGroup(
        ownerId: String,
        name: String,
        description: String,
        isPublic: Boolean,
        extraCoins: Long = 0L
    ): Result<GroupEntity> {
        val owner = dao.getUserById(ownerId) ?: return Result.failure(IllegalStateException("User missing"))
        val cost = AppConstants.GROUP_PURCHASE_COINS + extraCoins.coerceAtLeast(0L)
        if (owner.walletCoins < cost) {
            return Result.failure(IllegalArgumentException("Minimum ${AppConstants.GROUP_PURCHASE_COINS} coins required to buy a group"))
        }
        val cleanName = name.trim()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Group name required"))

        val now = System.currentTimeMillis()
        val group = GroupEntity(
            id = "grp_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            description = description.trim().ifBlank { "Community group on Millionaire" },
            ownerId = owner.id,
            isPublic = isPublic,
            memberLimit = AppConstants.GROUP_INITIAL_MEMBER_LIMIT,
            createdAt = now,
            expiresAt = now + TimeUnit.DAYS.toMillis(AppConstants.GROUP_LIFE_DAYS)
        )
        dao.updateUser(owner.copy(walletCoins = owner.walletCoins - cost))
        dao.upsertGroup(group)
        dao.upsertGroupMember(
            GroupMemberEntity(
                id = "gm_${UUID.randomUUID().toString().take(8)}",
                groupId = group.id,
                userId = owner.id,
                joinedAt = now
            )
        )
        dao.insertGroupMessage(
            GroupMessageEntity(
                id = "gmsg_${UUID.randomUUID().toString().take(8)}",
                groupId = group.id,
                senderId = owner.id,
                senderName = owner.name,
                text = "Group created! Initial limit: 50 members, life: 30 days.",
                timestamp = now
            )
        )
        return Result.success(group)
    }

    suspend fun renewGroupLife(ownerId: String, groupId: String): Result<Unit> {
        val owner = dao.getUserById(ownerId) ?: return Result.failure(IllegalStateException("User missing"))
        val group = dao.getGroupById(groupId) ?: return Result.failure(IllegalStateException("Group missing"))
        if (group.ownerId != ownerId) return Result.failure(IllegalAccessException("Only group owner can renew"))
        if (owner.walletCoins < AppConstants.GROUP_PURCHASE_COINS) {
            return Result.failure(IllegalArgumentException("200 coins required to update group life"))
        }
        val now = System.currentTimeMillis()
        val newBase = maxOf(now, group.expiresAt)
        dao.updateUser(owner.copy(walletCoins = owner.walletCoins - AppConstants.GROUP_PURCHASE_COINS))
        dao.upsertGroup(
            group.copy(
                expiresAt = newBase + TimeUnit.DAYS.toMillis(AppConstants.GROUP_LIFE_DAYS),
                renewalCount = group.renewalCount + 1
            )
        )
        return Result.success(Unit)
    }

    suspend fun addGroupFund(donorId: String, groupId: String, amount: Long): Result<Unit> {
        val donor = dao.getUserById(donorId) ?: return Result.failure(IllegalStateException("User missing"))
        val group = dao.getGroupById(groupId) ?: return Result.failure(IllegalStateException("Group missing"))
        val minRequired = if (group.renewalCount > 0) AppConstants.MIN_GROUP_FUND_RENEWAL_USE else AppConstants.MIN_GROUP_FUND_INITIAL
        if (amount < minRequired) {
            return Result.failure(IllegalArgumentException("Minimum $minRequired coins required to add fund and raise member limit"))
        }
        if (donor.walletCoins < amount) {
            return Result.failure(IllegalArgumentException("Insufficient wallet coins"))
        }
        val seatsAdded = ((amount / 50L) * 10L).toInt().coerceAtLeast(10)
        dao.updateUser(donor.copy(walletCoins = donor.walletCoins - amount))
        dao.upsertGroup(
            group.copy(
                memberLimit = group.memberLimit + seatsAdded,
                currentFund = group.currentFund + amount,
                totalFundUsed = group.totalFundUsed + amount
            )
        )
        dao.insertGroupDonation(
            GroupDonationEntity(
                id = "don_${UUID.randomUUID().toString().take(8)}",
                groupId = group.id,
                donorId = donor.id,
                donorName = donor.name,
                amount = amount,
                seatsAdded = seatsAdded,
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    suspend fun joinGroup(userId: String, groupId: String, useGloriousBypass: Boolean = false): Result<Unit> {
        val user = dao.getUserById(userId) ?: return Result.failure(IllegalStateException("User missing"))
        val group = dao.getGroupById(groupId) ?: return Result.failure(IllegalStateException("Group missing"))
        val level = AuthorityEngine.getLevel(user.authorityCoins)
        val existing = dao.getGroupMember(groupId, userId)
        val now = System.currentTimeMillis()

        if (existing != null && existing.isBlacklisted) {
            if (level >= 16 && useGloriousBypass) {
                if (user.walletCoins < AppConstants.GLORIOUS_BYPASS_COINS) {
                    return Result.failure(IllegalArgumentException("50 coins required for 1-hour Glorious bypass"))
                }
                dao.updateUser(user.copy(walletCoins = user.walletCoins - AppConstants.GLORIOUS_BYPASS_COINS))
                dao.upsertGroupMember(existing.copy(paidBypassUntil = now + TimeUnit.HOURS.toMillis(1)))
                return Result.success(Unit)
            }
            if (existing.paidBypassUntil > now) {
                return Result.success(Unit)
            }
            return Result.failure(IllegalAccessException("You are blacklisted from this group"))
        }

        if (existing != null) return Result.success(Unit)

        if (!group.isPublic && !AuthorityEngine.canEnterPrivateGroup(level) && group.ownerId != userId) {
            return Result.failure(IllegalAccessException("Private group! Invitation or Level 14+ required."))
        }

        val count = dao.countActiveGroupMembers(groupId)
        if (count >= group.memberLimit) {
            return Result.failure(IllegalStateException("Group member limit (${group.memberLimit}) reached. Add Group Fund to expand!"))
        }

        dao.upsertGroupMember(
            GroupMemberEntity(
                id = "gm_${UUID.randomUUID().toString().take(8)}",
                groupId = groupId,
                userId = userId,
                joinedAt = now
            )
        )
        return Result.success(Unit)
    }

    suspend fun inviteOrAddMember(actorId: String, groupId: String, targetUserId: String): Result<Unit> {
        val group = dao.getGroupById(groupId) ?: return Result.failure(IllegalStateException("Group missing"))
        val actorMember = dao.getGroupMember(groupId, actorId)
        val isOwner = group.ownerId == actorId
        val isAllowedSupervisor = actorMember?.isSupervisor == true && group.supervisorCanAddMembers
        if (!isOwner && !isAllowedSupervisor) {
            return Result.failure(IllegalAccessException("No permission to add members"))
        }
        val target = dao.getUserById(targetUserId) ?: return Result.failure(IllegalArgumentException("User not found"))
        if (!target.receiveGroupInvites && !isOwner) {
            return Result.failure(IllegalStateException("${target.name} has Group Invitations turned OFF"))
        }
        val count = dao.countActiveGroupMembers(groupId)
        if (count >= group.memberLimit) {
            return Result.failure(IllegalStateException("Group member limit (${group.memberLimit}) reached"))
        }
        val existing = dao.getGroupMember(groupId, targetUserId)
        if (existing != null && !existing.isBlacklisted) {
            return Result.failure(IllegalStateException("Already a member"))
        }
        dao.upsertGroupMember(
            GroupMemberEntity(
                id = existing?.id ?: "gm_${UUID.randomUUID().toString().take(8)}",
                groupId = groupId,
                userId = targetUserId,
                isBlacklisted = false,
                joinedAt = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    suspend fun removeOrBlacklistMember(
        actorId: String,
        groupId: String,
        targetUserId: String,
        blacklist: Boolean
    ): Result<Unit> {
        val group = dao.getGroupById(groupId) ?: return Result.failure(IllegalStateException("Group missing"))
        if (targetUserId == group.ownerId || targetUserId == AppConstants.OWNER_ID) {
            return Result.failure(IllegalAccessException("Group Owner cannot be kicked or removed"))
        }
        val actorMember = dao.getGroupMember(groupId, actorId)
        val isOwner = group.ownerId == actorId
        val allowed = if (blacklist) {
            isOwner || (actorMember?.isSupervisor == true && group.supervisorCanBlacklist)
        } else {
            isOwner || (actorMember?.isSupervisor == true && group.supervisorCanRemoveMembers)
        }
        if (!allowed) return Result.failure(IllegalAccessException("Permission denied by Owner"))

        val existing = dao.getGroupMember(groupId, targetUserId)
        if (blacklist) {
            dao.upsertGroupMember(
                (existing ?: GroupMemberEntity("gm_${UUID.randomUUID().toString().take(8)}", groupId, targetUserId, joinedAt = System.currentTimeMillis()))
                    .copy(isBlacklisted = true, isSupervisor = false)
            )
        } else {
            dao.removeGroupMember(groupId, targetUserId)
        }
        return Result.success(Unit)
    }

    suspend fun unblacklistMember(actorId: String, groupId: String, targetUserId: String): Result<Unit> {
        val group = dao.getGroupById(groupId) ?: return Result.failure(IllegalStateException("Group missing"))
        val actorMember = dao.getGroupMember(groupId, actorId)
        if (group.ownerId != actorId && !(actorMember?.isSupervisor == true && group.supervisorCanBlacklist)) {
            return Result.failure(IllegalAccessException("Permission denied"))
        }
        dao.removeGroupMember(groupId, targetUserId)
        return Result.success(Unit)
    }

    suspend fun toggleSupervisor(actorId: String, groupId: String, targetUserId: String, makeSupervisor: Boolean): Result<Unit> {
        val group = dao.getGroupById(groupId) ?: return Result.failure(IllegalStateException("Group missing"))
        if (group.ownerId != actorId) return Result.failure(IllegalAccessException("Only Group Owner can appoint supervisors"))
        if (targetUserId == group.ownerId) return Result.failure(IllegalArgumentException("Owner already controls the group"))
        val targetUser = dao.getUserById(targetUserId) ?: return Result.failure(IllegalArgumentException("User missing"))
        val level = AuthorityEngine.getLevel(targetUser.authorityCoins)
        if (makeSupervisor && !AuthorityEngine.canBeSupervisor(level)) {
            return Result.failure(IllegalStateException("Supervisor banne ke liye minimum Level 2 Authority zaroori hai"))
        }
        if (makeSupervisor && dao.countGroupSupervisors(groupId) >= AppConstants.MAX_SUPERVISORS_PER_GROUP) {
            return Result.failure(IllegalStateException("Maximum 5 supervisors allowed per group"))
        }
        val member = dao.getGroupMember(groupId, targetUserId) ?: return Result.failure(IllegalStateException("User not in group"))
        dao.upsertGroupMember(member.copy(isSupervisor = makeSupervisor))
        return Result.success(Unit)
    }

    suspend fun updateGroupSettings(
        actorId: String,
        groupId: String,
        isPublic: Boolean,
        canAdd: Boolean,
        canRemove: Boolean,
        canBlacklist: Boolean,
        canTogglePrivacy: Boolean
    ): Result<Unit> {
        val group = dao.getGroupById(groupId) ?: return Result.failure(IllegalStateException("Group missing"))
        val actorMember = dao.getGroupMember(groupId, actorId)
        val isOwner = group.ownerId == actorId
        if (!isOwner && !(actorMember?.isSupervisor == true && group.supervisorCanTogglePrivacy)) {
            return Result.failure(IllegalAccessException("Permission denied"))
        }
        dao.upsertGroup(
            if (isOwner) {
                group.copy(
                    isPublic = isPublic,
                    supervisorCanAddMembers = canAdd,
                    supervisorCanRemoveMembers = canRemove,
                    supervisorCanBlacklist = canBlacklist,
                    supervisorCanTogglePrivacy = canTogglePrivacy
                )
            } else {
                group.copy(isPublic = isPublic)
            }
        )
        return Result.success(Unit)
    }

    suspend fun sendGroupMessage(
        senderId: String,
        groupId: String,
        text: String,
        taggedNames: String
    ): Result<Unit> {
        val clean = text.trim()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Empty message"))
        val sender = dao.getUserById(senderId) ?: return Result.failure(IllegalStateException("User missing"))
        val group = dao.getGroupById(groupId) ?: return Result.failure(IllegalStateException("Group missing"))
        val member = dao.getGroupMember(groupId, senderId) ?: return Result.failure(IllegalAccessException("Join group first"))
        val now = System.currentTimeMillis()
        if (member.kickedUntil > now) {
            val mins = ((member.kickedUntil - now) / 60000L).coerceAtLeast(1L)
            return Result.failure(IllegalAccessException("You are kicked from this group for $mins more minute(s)"))
        }
        if (group.ownerId == senderId && group.ownerChatBannedUntil > now) {
            val mins = ((group.ownerChatBannedUntil - now) / 60000L).coerceAtLeast(1L)
            return Result.failure(IllegalAccessException("You are banned from chatting in this group for $mins min(s). Other groups & inbox still work!"))
        }
        dao.insertGroupMessage(
            GroupMessageEntity(
                id = "gmsg_${UUID.randomUUID().toString().take(8)}",
                groupId = groupId,
                senderId = sender.id,
                senderName = sender.name,
                taggedUserNames = taggedNames,
                text = clean,
                timestamp = now
            )
        )
        return Result.success(Unit)
    }

    suspend fun kickMemberInGroup(
        actorId: String,
        groupId: String,
        targetUserId: String,
        reason: String
    ): Result<Int> {
        val actor = dao.getUserById(actorId) ?: return Result.failure(IllegalStateException("User missing"))
        val target = dao.getUserById(targetUserId) ?: return Result.failure(IllegalStateException("Target missing"))
        val group = dao.getGroupById(groupId) ?: return Result.failure(IllegalStateException("Group missing"))
        if (targetUserId == group.ownerId || targetUserId == AppConstants.OWNER_ID) {
            return Result.failure(IllegalAccessException("Group Owner cannot be kicked"))
        }
        val actorMember = dao.getGroupMember(groupId, actorId)
            ?: return Result.failure(IllegalAccessException("Must be in the same group"))
        val targetMember = dao.getGroupMember(groupId, targetUserId)
            ?: return Result.failure(IllegalAccessException("Target must be in the same group"))

        val now = System.currentTimeMillis()
        if (actorMember.isBlacklisted && actorMember.paidBypassUntil > now) {
            return Result.failure(IllegalAccessException("Kick options are disabled during 1-hour bypass"))
        }

        val isSupervisorOrOwner = group.ownerId == actorId || actorMember.isSupervisor
        val actorLevel = AuthorityEngine.getLevel(actor.authorityCoins)

        val kickMinutes = if (isSupervisorOrOwner) {
            24 * 60 // 24 hours
        } else {
            if (actorLevel < 3) {
                return Result.failure(IllegalAccessException("Minimum Level 3 Authority or Supervisor required to kick"))
            }
            val cooldownMs = AuthorityEngine.kickCooldownMinutes(actorLevel) * 60_000L
            if (actor.lastKickAt > 0L && (now - actor.lastKickAt) < cooldownMs) {
                val remMin = ((cooldownMs - (now - actor.lastKickAt)) / 60_000L).coerceAtLeast(1L)
                return Result.failure(IllegalStateException("Kick cooldown active for Level $actorLevel ($remMin min left)"))
            }
            AuthorityEngine.kickDurationMinutes(actorLevel)
        }

        dao.updateUser(actor.copy(lastKickAt = now))
        dao.upsertGroupMember(
            targetMember.copy(
                kickedUntil = now + kickMinutes * 60_000L,
                kickedByUserId = actor.id,
                kickReason = reason.trim().ifBlank { "Abusive language / Misbehavior" }
            )
        )
        return Result.success(kickMinutes)
    }

    suspend fun banGroupOwnerInGroup(actorId: String, groupId: String): Result<Int> {
        val actor = dao.getUserById(actorId) ?: return Result.failure(IllegalStateException("User missing"))
        val group = dao.getGroupById(groupId) ?: return Result.failure(IllegalStateException("Group missing"))
        val actorMember = dao.getGroupMember(groupId, actorId)
            ?: return Result.failure(IllegalAccessException("Must be in the same group"))
        val now = System.currentTimeMillis()
        if (actorMember.isBlacklisted && actorMember.paidBypassUntil > now) {
            return Result.failure(IllegalAccessException("Ban options disabled during 1-hour bypass"))
        }
        val level = AuthorityEngine.getLevel(actor.authorityCoins)
        val banHours = AuthorityEngine.ownerChatBanHours(level)
        val cooldownHours = AuthorityEngine.ownerChatBanCooldownHours(level)
        if (banHours <= 0) {
            return Result.failure(IllegalAccessException("Level 14+ required to chat-ban Group Owner"))
        }
        val cooldownMs = cooldownHours * 3600_000L
        if (actor.lastOwnerBanAt > 0L && (now - actor.lastOwnerBanAt) < cooldownMs) {
            val remH = ((cooldownMs - (now - actor.lastOwnerBanAt)) / 3600_000L).coerceAtLeast(1L)
            return Result.failure(IllegalStateException("Cooldown active: can ban Group Owner again in ${remH}h"))
        }
        dao.updateUser(actor.copy(lastOwnerBanAt = now))
        dao.upsertGroup(group.copy(ownerChatBannedUntil = now + banHours * 3600_000L))
        return Result.success(banHours)
    }

    suspend fun fileKickComplaint(kickedUserId: String, groupId: String): Result<Unit> {
        val group = dao.getGroupById(groupId) ?: return Result.failure(IllegalStateException("Group missing"))
        val kickedMember = dao.getGroupMember(groupId, kickedUserId)
            ?: return Result.failure(IllegalStateException("Membership missing"))
        val kickedUser = dao.getUserById(kickedUserId) ?: return Result.failure(IllegalStateException("User missing"))
        val kickerId = kickedMember.kickedByUserId ?: return Result.failure(IllegalStateException("No recent kick record"))
        val kicker = dao.getUserById(kickerId) ?: return Result.failure(IllegalStateException("Kicker missing"))

        val last10 = dao.getLastTenGroupMessages(groupId).reversed()
        val formattedLog = if (last10.isEmpty()) {
            "(No recent messages)"
        } else {
            last10.joinToString("\n") { "${it.senderName}: ${it.text}" }
        }

        dao.upsertComplaint(
            SystemComplaintEntity(
                id = "cmp_${UUID.randomUUID().toString().take(8)}",
                groupId = group.id,
                groupName = group.name,
                groupOwnerId = group.ownerId,
                kickerId = kicker.id,
                kickerName = kicker.name,
                kickedUserId = kickedUser.id,
                kickedUserName = kickedUser.name,
                chatLogTenLines = formattedLog,
                status = "PENDING",
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    suspend fun resolveComplaint(ownerId: String, complaintId: String, punishKicker: Boolean): Result<String> {
        val complaint = dao.getComplaintById(complaintId) ?: return Result.failure(IllegalStateException("Not found"))
        if (complaint.groupOwnerId != ownerId) return Result.failure(IllegalAccessException("Only Group Owner can decide"))
        if (complaint.status != "PENDING") return Result.failure(IllegalStateException("Already resolved"))

        if (!punishKicker) {
            dao.upsertComplaint(complaint.copy(status = "IGNORED"))
            return Result.success("Complaint ignored.")
        }

        val kicker = dao.getUserById(complaint.kickerId)
        val kickerMember = dao.getGroupMember(complaint.groupId, complaint.kickerId)
        if (kicker == null) {
            dao.upsertComplaint(complaint.copy(status = "IGNORED"))
            return Result.success("User no longer exists.")
        }

        val level = AuthorityEngine.getLevel(kicker.authorityCoins)
        val immunityLimit = AuthorityEngine.unfairKickImmunityCount(level)
        val newUnfairCount = kicker.unfairKickCount + 1

        return if (newUnfairCount <= immunityLimit) {
            dao.updateUser(kicker.copy(unfairKickCount = newUnfairCount))
            dao.upsertComplaint(complaint.copy(status = "IGNORED"))
            Result.success("Level $level immunity protected ${kicker.name} ($newUnfairCount/$immunityLimit unfair kicks used).")
        } else {
            dao.updateUser(kicker.copy(unfairKickCount = newUnfairCount))
            if (kickerMember != null) {
                dao.upsertGroupMember(
                    kickerMember.copy(
                        kickedUntil = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24),
                        kickedByUserId = ownerId,
                        kickReason = "Unfair kick punishment (24h)"
                    )
                )
            }
            dao.upsertComplaint(complaint.copy(status = "PUNISHED"))
            Result.success("${kicker.name} kicked from ${complaint.groupName} for 24 hours.")
        }
    }

    // --- RATINGS ---

    suspend fun submitAppRating(userId: String, stars: Int, comment: String): Result<Unit> {
        val user = dao.getUserById(userId) ?: return Result.failure(IllegalStateException("User missing"))
        dao.insertRating(
            AppRatingEntity(
                id = "rat_${UUID.randomUUID().toString().take(8)}",
                userId = user.id,
                userName = user.name,
                stars = stars.coerceIn(1, 5),
                comment = comment.trim().ifBlank { "Great community app!" },
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }
}

