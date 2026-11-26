package com.example.data.local

import com.example.security.PasswordHasher
import java.util.concurrent.TimeUnit

object DatabaseSeeder {
    suspend fun seedIfEmpty(dao: AppDao) {
        if (dao.countUsers() > 0) return

        val now = System.currentTimeMillis()
        val dayMs = TimeUnit.DAYS.toMillis(1)

        val owner = UserEntity(
            id = AppConstants.OWNER_ID,
            name = "Anwar Malik",
            email = "owner@raabta.app",
            mobileNumber = "03001112233",
            passwordHash = PasswordHasher.hashPassword("Owner@123"),
            role = UserRoles.OWNER,
            developerLevel = 0,
            devCanResetPassword = true,
            devCanBlockUsers = true,
            devCanApproveCoins = true,
            devCanModerateGroups = true,
            walletCoins = 5000L,
            authorityCoins = 389_357_350L, // Level 16
            showCoinsOnProfile = true
        )

        val dev = UserEntity(
            id = "dev_001",
            name = "Zainab Raza",
            email = "zainab@raabta.app",
            mobileNumber = "03012223344",
            passwordHash = PasswordHasher.hashPassword("Dev@1234"),
            role = UserRoles.DEVELOPER,
            developerLevel = 2,
            devCanResetPassword = true,
            devCanBlockUsers = true,
            devCanApproveCoins = false,
            devCanModerateGroups = true,
            walletCoins = 1800L,
            authorityCoins = 3_809_375L, // Level 11
            showCoinsOnProfile = true
        )

        val bilal = UserEntity(
            id = "user_001",
            name = "Bilal Ahmed",
            email = "bilal@raabta.app",
            mobileNumber = "03113334455",
            passwordHash = PasswordHasher.hashPassword("User@123"),
            role = UserRoles.USER,
            walletCoins = 650L,
            authorityCoins = 500L, // Level 3 (10m kick)
            showCoinsOnProfile = true
        )

        val sara = UserEntity(
            id = "user_002",
            name = "Sara Khan",
            email = "sara@raabta.app",
            mobileNumber = "03214445566",
            passwordHash = PasswordHasher.hashPassword("User@123"),
            role = UserRoles.USER,
            walletCoins = 1200L,
            authorityCoins = 70_701_250L, // Level 14 (Private access + 2h owner chat ban)
            showCoinsOnProfile = true
        )

        val hamza = UserEntity(
            id = "user_003",
            name = "Hamza Tariq",
            email = "hamza@raabta.app",
            mobileNumber = "03335556677",
            passwordHash = PasswordHasher.hashPassword("User@123"),
            role = UserRoles.USER,
            walletCoins = 950L,
            authorityCoins = 389_357_350L, // Level 16 (50-coin blacklist bypass, 5 unfair immunity)
            showCoinsOnProfile = false
        )

        val ayesha = UserEntity(
            id = "user_004",
            name = "Ayesha Noor",
            email = "ayesha@raabta.app",
            mobileNumber = "03456667788",
            passwordHash = PasswordHasher.hashPassword("User@123"),
            role = UserRoles.USER,
            walletCoins = 420L,
            authorityCoins = 80L, // Level 2 (Supervisor eligible)
            showCoinsOnProfile = true
        )

        dao.insertUsers(listOf(owner, dev, bilal, sara, hamza, ayesha))

        // Friendships
        dao.upsertFriendRelation(FriendRelationEntity("fr_1", AppConstants.OWNER_ID, "dev_001", "ACCEPTED", AppConstants.OWNER_ID, now - dayMs * 5))
        dao.upsertFriendRelation(FriendRelationEntity("fr_2", AppConstants.OWNER_ID, "user_001", "ACCEPTED", "user_001", now - dayMs * 3))
        dao.upsertFriendRelation(FriendRelationEntity("fr_3", AppConstants.OWNER_ID, "user_002", "ACCEPTED", "user_002", now - dayMs * 2))
        dao.upsertFriendRelation(FriendRelationEntity("fr_4", "user_001", "user_002", "ACCEPTED", "user_001", now - dayMs * 2))
        dao.upsertFriendRelation(FriendRelationEntity("fr_5", "user_001", "user_004", "PENDING", "user_004", now - 3600_000L))
        dao.upsertFriendRelation(FriendRelationEntity("fr_6", AppConstants.OWNER_ID, "user_004", "PENDING", "user_004", now - 1800_000L))

        // Direct Messages
        dao.insertDirectMessages(
            listOf(
                DirectMessageEntity("dm_1", "dev_001", AppConstants.OWNER_ID, "Assalam-o-Alaikum Boss, new coin orders are ready for review.", now - 7200_000L),
                DirectMessageEntity("dm_2", AppConstants.OWNER_ID, "dev_001", "Walaikum Assalam Zainab! Checking the Admin Panel now.", now - 7000_000L),
                DirectMessageEntity("dm_3", "user_001", AppConstants.OWNER_ID, "Bhai how do I reach Level 4 Authority?", now - 5400_000L),
                DirectMessageEntity("dm_4", AppConstants.OWNER_ID, "user_001", "Watch ads or buy coins via JazzCash/EasyPaisa, then boost Authority in Menu!", now - 5100_000L),
                DirectMessageEntity("dm_5", "user_002", "user_001", "Hey Bilal! Join Karachi Tech Hub group.", now - 3200_000L)
            )
        )

        // Groups
        val g1 = GroupEntity(
            id = "grp_001",
            name = "Karachi Tech Hub",
            description = "Public community for builders, traders, and friends.",
            ownerId = AppConstants.OWNER_ID,
            isPublic = true,
            memberLimit = 70,
            createdAt = now - 10 * dayMs,
            expiresAt = now + 20 * dayMs,
            currentFund = 100L,
            totalFundUsed = 100L,
            supervisorCanAddMembers = true,
            supervisorCanRemoveMembers = true,
            supervisorCanBlacklist = true,
            supervisorCanTogglePrivacy = false
        )

        val g2 = GroupEntity(
            id = "grp_002",
            name = "Lahore Legends",
            description = "Active hangouts group — approaching 30-day renewal!",
            ownerId = AppConstants.OWNER_ID,
            isPublic = true,
            memberLimit = 50,
            createdAt = now - 26 * dayMs,
            expiresAt = now + 4 * dayMs, // <= 5 days left -> triggers 25-day renewal prompt!
            currentFund = 50L,
            totalFundUsed = 50L,
            supervisorCanAddMembers = true,
            supervisorCanRemoveMembers = false,
            supervisorCanBlacklist = false,
            supervisorCanTogglePrivacy = false
        )

        val g3 = GroupEntity(
            id = "grp_003",
            name = "VIP Founders Lounge",
            description = "Private group — accessible to invited members & Level 14+.",
            ownerId = "user_001",
            isPublic = false,
            memberLimit = 50,
            createdAt = now - 3 * dayMs,
            expiresAt = now + 27 * dayMs,
            currentFund = 0L,
            totalFundUsed = 0L
        )

        dao.insertGroups(listOf(g1, g2, g3))

        dao.insertGroupMembers(
            listOf(
                GroupMemberEntity("gm_1", "grp_001", AppConstants.OWNER_ID, isSupervisor = false, joinedAt = now - 10 * dayMs),
                GroupMemberEntity("gm_2", "grp_001", "dev_001", isSupervisor = true, joinedAt = now - 9 * dayMs),
                GroupMemberEntity("gm_3", "grp_001", "user_001", isSupervisor = false, joinedAt = now - 8 * dayMs),
                GroupMemberEntity("gm_4", "grp_001", "user_002", isSupervisor = false, joinedAt = now - 7 * dayMs),
                GroupMemberEntity("gm_5", "grp_001", "user_003", isSupervisor = false, joinedAt = now - 6 * dayMs),
                GroupMemberEntity("gm_6", "grp_001", "user_004", isSupervisor = true, joinedAt = now - 5 * dayMs),

                GroupMemberEntity("gm_7", "grp_002", AppConstants.OWNER_ID, isSupervisor = false, joinedAt = now - 26 * dayMs),
                GroupMemberEntity("gm_8", "grp_002", "user_001", isSupervisor = false, joinedAt = now - 20 * dayMs),
                GroupMemberEntity("gm_9", "grp_002", "user_002", isSupervisor = false, joinedAt = now - 15 * dayMs),

                GroupMemberEntity("gm_10", "grp_003", "user_001", isSupervisor = false, joinedAt = now - 3 * dayMs),
                GroupMemberEntity("gm_11", "grp_003", "user_002", isSupervisor = false, joinedAt = now - 2 * dayMs)
            )
        )

        dao.insertGroupMessages(
            listOf(
                GroupMessageEntity("gmsg_1", "grp_001", AppConstants.OWNER_ID, "Anwar Malik", "", "Welcome everyone to Karachi Tech Hub! Tap any name to @tag and reply.", now - 3600_000L * 5),
                GroupMessageEntity("gmsg_2", "grp_001", "user_001", "Bilal Ahmed", "", "Thanks Anwar! Just donated 100 coins to expand the group seats.", now - 3600_000L * 4),
                GroupMessageEntity("gmsg_3", "grp_001", "user_002", "Sara Khan", "@Bilal Ahmed", "Nice! I also hit Level 14 Authority today.", now - 3600_000L * 3),
                GroupMessageEntity("gmsg_4", "grp_001", "user_004", "Ayesha Noor", "", "Glad to be appointed as a Group Supervisor!", now - 3600_000L * 2),
                GroupMessageEntity("gmsg_5", "grp_002", AppConstants.OWNER_ID, "Anwar Malik", "", "Lahore Legends is almost 26 days old — remember to renew group life!", now - 3600_000L * 3),
                GroupMessageEntity("gmsg_6", "grp_002", "user_001", "Bilal Ahmed", "", "Count me in for the next round of group fund donations.", now - 3600_000L * 2),
                GroupMessageEntity("gmsg_7", "grp_003", "user_001", "Bilal Ahmed", "", "Welcome to VIP Founders Lounge (Private).", now - 3600_000L * 2)
            )
        )

        dao.insertGroupDonation(
            GroupDonationEntity("don_1", "grp_001", "user_001", "Bilal Ahmed", 100L, 20, now - 3600_000L * 4)
        )

        dao.upsertCoinOrder(
            CoinOrderEntity(
                id = "ord_001",
                userId = "user_001",
                userName = "Bilal Ahmed",
                paymentMethod = "JazzCash",
                coinsRequested = 200L,
                amountPkr = 10_000L,
                referenceNote = "JC-TRX-884920",
                status = "PENDING",
                timestamp = now - 1800_000L
            )
        )

        dao.upsertCoinOrder(
            CoinOrderEntity(
                id = "ord_002",
                userId = "user_002",
                userName = "Sara Khan",
                paymentMethod = "USDT",
                coinsRequested = 50L,
                amountPkr = 2_500L,
                referenceNote = "TRC20-0x99A1...F4",
                status = "PENDING",
                timestamp = now - 900_000L
            )
        )

        dao.insertRating(AppRatingEntity("rat_1", "user_002", "Sara Khan", 5, "Super clean WhatsApp-like chat and fair Authority levels!", now - dayMs))
        dao.insertRating(AppRatingEntity("rat_2", "user_001", "Bilal Ahmed", 4, "Love earning coins from ads and expanding group funds.", now - 7200_000L))
    }
}
