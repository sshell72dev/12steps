package ru.na.step4.obidy.data.spiritual

object SpiritualEconomy {
    const val BASE_ANALYSIS = 5
    const val BASE_JOURNAL = 3
    const val BASE_PSYCH = 4
    const val BASE_SUPPORT = 2
    const val FICTITIOUS_PENALTY = -3

    const val RATE_MIN = 0.50f
    const val RATE_MAX = 2.00f
    const val RATE_PRACTICE_BUMP = 0.05f
    const val RATE_HIGH = 0.03f
    const val RATE_LOW = -0.08f
    const val RATE_FICTITIOUS = -0.15f

    fun penaltyForMissDay(k: Int): Int {
        if (k <= 0) return 0
        return (-(2 + k)).coerceAtLeast(-15)
    }

    fun clampRate(rate: Float): Float = rate.coerceIn(RATE_MIN, RATE_MAX)

    fun scaledPoints(base: Int, rate: Float): Int {
        if (base == 0) return 0
        val raw = kotlin.math.round(base * rate).toInt()
        return when {
            raw == 0 && base > 0 -> 1
            raw == 0 && base < 0 -> -1
            else -> raw
        }
    }

    fun rateAfterQuality(rate: Float, quality: SpiritualQuality): Float = clampRate(
        when (quality) {
            SpiritualQuality.HIGH -> rate + RATE_HIGH
            SpiritualQuality.OK -> rate
            SpiritualQuality.LOW -> rate + RATE_LOW
            SpiritualQuality.FICTITIOUS -> rate + RATE_FICTITIOUS
        }
    )

    fun rateAfterMissStreak(rate: Float, missStreak: Int): Float {
        if (missStreak < 2) return rate
        val drop = 0.10f * missStreak.coerceAtMost(5)
        return clampRate(rate - drop)
    }

    fun clampTotal(total: Int): Int = total.coerceAtLeast(0)

    fun clampAiScore(score: Int): Int = score.coerceIn(-5, 10)

    fun dayLabel(dayScore: Int): String = when {
        dayScore < 0 -> SpiritualRu.dayDip
        dayScore == 0 -> SpiritualRu.daySilence
        dayScore in 1..9 -> SpiritualRu.daySoft
        dayScore in 10..19 -> SpiritualRu.dayWorking
        else -> SpiritualRu.dayDeep
    }

    fun sourceLabel(source: SpiritualSource): String = when (source) {
        SpiritualSource.ANALYSIS -> SpiritualRu.srcAnalysis
        SpiritualSource.JOURNAL -> SpiritualRu.srcJournal
        SpiritualSource.PSYCH -> SpiritualRu.srcPsych
        SpiritualSource.SUPPORT -> SpiritualRu.srcSupport
        SpiritualSource.AI -> SpiritualRu.srcAi
        SpiritualSource.MISS -> SpiritualRu.srcMiss
    }

    fun baseFor(source: SpiritualSource): Int = when (source) {
        SpiritualSource.ANALYSIS -> BASE_ANALYSIS
        SpiritualSource.JOURNAL -> BASE_JOURNAL
        SpiritualSource.PSYCH -> BASE_PSYCH
        SpiritualSource.SUPPORT -> BASE_SUPPORT
        else -> 0
    }
}
