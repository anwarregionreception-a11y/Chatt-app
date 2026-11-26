package com.example.domain

enum class BadgeType(
    val displayName: String,
    val coinValue: Long,
    val isGem: Boolean
) {
    WHITE_DIAMOND("White Diamond", 146_484_375L, true),
    BLACK_GEM("Black", 29_296_875L, true),
    YELLOW_GEM("Yellow", 5_859_375L, true),
    PURPLE_GEM("Purple", 1_171_875L, true),
    CYAN_GEM("Cyan", 234_375L, true),
    GREEN_GEM("Green", 46_875L, true),
    BLUE_GEM("Blue", 9_375L, true),
    RED_GEM("Red", 1_875L, true),
    DARK_GOLD_STAR("Dark Gold", 375L, false),
    SILVER_STAR("Silver", 75L, false),
    LIGHT_GOLD_STAR("Light Gold", 25L, false),
    ORANGE_STAR("Orange", 5L, false),
    OUTLINE_STAR("Outline Star", 1L, false)
}

data class BadgeCount(
    val badge: BadgeType,
    val count: Int
)

data class LevelThreshold(
    val level: Int,
    val minCoins: Long
)

object AuthorityEngine {
    val levelThresholds: List<LevelThreshold> = listOf(
        LevelThreshold(16, 389_357_350L),
        LevelThreshold(15, 276_902_725L),
        LevelThreshold(14, 70_701_250L),
        LevelThreshold(13, 50_918_375L),
        LevelThreshold(12, 9_716_875L),
        LevelThreshold(11, 3_809_375L),
        LevelThreshold(10, 1_000_000L),
        LevelThreshold(9, 237_500L),
        LevelThreshold(8, 100_000L),
        LevelThreshold(7, 50_000L),
        LevelThreshold(6, 10_000L),
        LevelThreshold(5, 5_000L),
        LevelThreshold(4, 1_000L),
        LevelThreshold(3, 500L),
        LevelThreshold(2, 80L),
        LevelThreshold(1, 1L)
    )

    fun coinsToBadges(totalCoins: Long): List<BadgeCount> {
        if (totalCoins <= 0L) return emptyList()
        val result = mutableListOf<BadgeCount>()
        var remaining = totalCoins
        for (badge in BadgeType.entries) {
            val count = (remaining / badge.coinValue).toInt()
            if (count > 0) {
                result.add(BadgeCount(badge, count))
                remaining %= badge.coinValue
            }
        }
        return result
    }

    fun getLevel(totalCoins: Long): Int {
        for (threshold in levelThresholds) {
            if (totalCoins >= threshold.minCoins) {
                return threshold.level
            }
        }
        return 0
    }

    fun nextLevelThreshold(currentLevel: Int): Long? {
        if (currentLevel >= 16) return null
        return levelThresholds.firstOrNull { it.level == currentLevel + 1 }?.minCoins
    }

    fun canBeSupervisor(level: Int): Boolean = level >= 2

    fun canEnterPrivateGroup(level: Int): Boolean = level >= 14

    fun kickDurationMinutes(level: Int): Int = when (level) {
        3 -> 10
        4 -> 40
        5 -> 60
        6 -> 70
        7 -> 90
        8 -> 140
        9 -> 190
        10 -> 250
        11 -> 310
        12 -> 370
        13 -> 430
        14 -> 490
        15 -> 550
        16 -> 610
        else -> 0
    }

    fun kickCooldownMinutes(level: Int): Int = when (level) {
        1 -> 24 * 60
        2 -> 22 * 60
        3 -> 20 * 60
        4 -> 18 * 60
        5 -> 16 * 60
        6 -> 14 * 60
        7 -> 12 * 60
        8 -> 10 * 60
        9 -> 8 * 60
        10 -> 6 * 60
        11 -> 4 * 60
        12 -> 3 * 60
        13 -> 2 * 60
        14 -> 60
        15 -> 30
        16 -> 1
        else -> 24 * 60
    }

    fun unfairKickImmunityCount(level: Int): Int = when (level) {
        12 -> 1
        13 -> 2
        14 -> 3
        15 -> 4
        16 -> 5
        else -> 0
    }

    fun ownerChatBanHours(level: Int): Int = when (level) {
        14 -> 2
        15 -> 3
        16 -> 4
        else -> 0
    }

    fun ownerChatBanCooldownHours(level: Int): Int = when (level) {
        14 -> 24
        15 -> 24
        16 -> 12
        else -> 0
    }

    fun formatDurationMinutes(minutes: Int): String {
        if (minutes < 60) return "${minutes}m"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "${h}h" else "${h}h ${m}m"
    }
}
