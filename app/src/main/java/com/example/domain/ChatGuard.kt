package com.example.domain

/** Detects website and app links, which are not allowed in chat. */
object LinkFilter {
    private val pattern = Regex(
        "(https?://|ftp://|www\\.)" +
            "|\\b(t|wa)\\.me/" +
            "|\\b[a-z0-9-]+\\.[a-z]{2,}/" +
            "|\\b[a-z0-9-]+\\.(com|net|org|pk|io|app|ly|gg|info|xyz|link|online|store|site|shop|biz|tk|live|club|top|dev)\\b",
        RegexOption.IGNORE_CASE
    )

    fun containsLink(text: String): Boolean = pattern.containsMatchIn(text)
}

sealed class FloodDecision {
    object Allowed : FloodDecision()
    object Warning : FloodDecision()
    data class Blocked(val secondsLeft: Long) : FloodDecision()
}

/**
 * A fourth message within two seconds counts as flooding.
 * The first time gives a warning, the next time blocks sending for 90 seconds.
 */
class FloodGuard {
    private val recent = mutableListOf<Long>()
    private var warned = false
    private var blockedUntil = 0L

    fun check(now: Long): FloodDecision {
        if (now < blockedUntil) {
            return FloodDecision.Blocked((blockedUntil - now + 999L) / 1000L)
        }
        recent.removeAll { now - it >= WINDOW_MS }
        if (recent.size >= MAX_IN_WINDOW) {
            recent.clear()
            if (!warned) {
                warned = true
                return FloodDecision.Warning
            }
            warned = false
            blockedUntil = now + BLOCK_MS
            return FloodDecision.Blocked(BLOCK_MS / 1000L)
        }
        recent.add(now)
        return FloodDecision.Allowed
    }

    private companion object {
        const val WINDOW_MS = 2_000L
        const val MAX_IN_WINDOW = 3
        const val BLOCK_MS = 90_000L
    }
}
