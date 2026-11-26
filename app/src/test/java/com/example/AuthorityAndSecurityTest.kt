package com.example

import com.example.domain.AuthorityEngine
import com.example.domain.BadgeType
import com.example.security.PasswordHasher
import org.junit.Assert.*
import org.junit.Test

class AuthorityAndSecurityTest {

    @Test
    fun greedyBadges_67Coins_matchesExample() {
        val badges = AuthorityEngine.coinsToBadges(67L)
        assertEquals(3, badges.size)
        assertEquals(BadgeType.LIGHT_GOLD_STAR, badges[0].badge)
        assertEquals(2, badges[0].count)
        assertEquals(BadgeType.ORANGE_STAR, badges[1].badge)
        assertEquals(3, badges[1].count)
        assertEquals(BadgeType.OUTLINE_STAR, badges[2].badge)
        assertEquals(2, badges[2].count)
    }

    @Test
    fun greedyBadges_100Coins_matchesExample() {
        val badges = AuthorityEngine.coinsToBadges(100L)
        assertEquals(2, badges.size)
        assertEquals(BadgeType.SILVER_STAR, badges[0].badge)
        assertEquals(1, badges[0].count)
        assertEquals(BadgeType.LIGHT_GOLD_STAR, badges[1].badge)
        assertEquals(1, badges[1].count)
    }

    @Test
    fun authorityLevels_exactThresholds() {
        assertEquals(0, AuthorityEngine.getLevel(0L))
        assertEquals(1, AuthorityEngine.getLevel(1L))
        assertEquals(1, AuthorityEngine.getLevel(67L))
        assertEquals(2, AuthorityEngine.getLevel(80L))
        assertEquals(3, AuthorityEngine.getLevel(500L))
        assertEquals(14, AuthorityEngine.getLevel(70_701_250L))
        assertEquals(15, AuthorityEngine.getLevel(276_902_725L))
        assertEquals(16, AuthorityEngine.getLevel(389_357_350L))
    }

    @Test
    fun authorityRules_cooldownsAndImmunity() {
        assertEquals(1440, AuthorityEngine.kickCooldownMinutes(1))
        assertEquals(1, AuthorityEngine.kickCooldownMinutes(16))
        assertEquals(1, AuthorityEngine.unfairKickImmunityCount(12))
        assertEquals(5, AuthorityEngine.unfairKickImmunityCount(16))
        assertEquals(2, AuthorityEngine.ownerChatBanHours(14))
        assertEquals(4, AuthorityEngine.ownerChatBanHours(16))
    }

    @Test
    fun passwordHasher_hashVerifyAndRules() {
        assertNotNull(PasswordHasher.validateNewPassword("short1A"))
        assertNotNull(PasswordHasher.validateNewPassword("alllowercase1"))
        assertNotNull(PasswordHasher.validateNewPassword("NoNumberHere"))
        assertNull(PasswordHasher.validateNewPassword("ValidPass1"))

        val hash = PasswordHasher.hashPassword("SecretPass9")
        assertTrue(PasswordHasher.verifyPassword("SecretPass9", hash))
        assertFalse(PasswordHasher.verifyPassword("WrongPass9", hash))

        val temp = PasswordHasher.generateTemporaryPassword()
        assertEquals(6, temp.length)
    }
}
