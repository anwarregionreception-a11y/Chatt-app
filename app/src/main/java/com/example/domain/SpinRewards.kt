package com.example.domain

/** Rewards of the daily spin. The wheel layout is in [SpinReward.WHEEL]. */
enum class SpinReward(val label: String, val coins: Long, val stars: Long) {
    ONE_COIN("1 Coin", 1L, 0L),
    THREE_COINS("3 Coins", 3L, 0L),
    ONE_STAR("1 Outline Star", 0L, 1L),
    THREE_STARS("3 Outline Stars", 0L, 3L),
    TRY_LATER("Try again after 24 hours", 0L, 0L);

    companion object {
        const val COOLDOWN_MS = 24L * 60L * 60L * 1000L

        /**
         * The wheel picture has six equal slices. Slice 0 is under the pointer at rest and the
         * rest follow clockwise. Must match the lists in firestore.rules.
         */
        val WHEEL: List<SpinReward> =
            listOf(THREE_COINS, ONE_STAR, TRY_LATER, THREE_STARS, ONE_STAR, ONE_COIN)

        /** The server time of the spin picks the slice. */
        fun slotForSpinTime(millis: Long): Int = (millis % WHEEL.size).toInt()

        fun forSpinTime(millis: Long): SpinReward = WHEEL[slotForSpinTime(millis)]
    }
}
