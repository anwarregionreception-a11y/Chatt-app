package com.example.domain

/** Order must match the reward lists in firestore.rules. */
enum class SpinReward(val label: String, val coins: Long, val stars: Long) {
    ONE_COIN("1 Coin", 1L, 0L),
    THREE_COINS("3 Coins", 3L, 0L),
    ONE_STAR("1 Outline Star", 0L, 1L),
    THREE_STARS("3 Outline Stars", 0L, 3L),
    TRY_LATER("Try again after 24 hours", 0L, 0L);

    companion object {
        const val COOLDOWN_MS = 24L * 60L * 60L * 1000L

        /** The server time of the spin picks the reward. */
        fun forSpinTime(millis: Long): SpinReward = entries[(millis % entries.size).toInt()]
    }
}
