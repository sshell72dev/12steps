package ru.na.step4.obidy.data.spiritual

enum class SpiritualSource {
    ANALYSIS,
    JOURNAL,
    PSYCH,
    SUPPORT,
    AI,
    MISS
}

enum class SpiritualQuality {
    HIGH,
    OK,
    LOW,
    FICTITIOUS;

    companion object {
        fun parse(raw: String?): SpiritualQuality = when (raw?.trim()?.lowercase()) {
            "high" -> HIGH
            "low" -> LOW
            "fictitious", "fake", "фиктивный" -> FICTITIOUS
            else -> OK
        }
    }
}

data class SpiritualEvent(
    val id: String,
    val source: SpiritualSource,
    val delta: Int,
    val reason: String,
    val at: Long
)

data class SpiritualSnapshot(
    val totalScore: Int = 0,
    val dayScore: Int = 0,
    val rate: Float = 1f,
    val practiceStreak: Int = 0,
    val missStreak: Int = 0,
    val dayLabel: String = SpiritualRu.daySilence,
    val recent: List<SpiritualEvent> = emptyList()
)

data class SpiritualDelta(
    val score: Int,
    val quality: SpiritualQuality,
    val reason: String,
    val visibleText: String
)
