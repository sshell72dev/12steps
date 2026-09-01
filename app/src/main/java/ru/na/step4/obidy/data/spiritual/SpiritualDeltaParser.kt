package ru.na.step4.obidy.data.spiritual

object SpiritualDeltaParser {
    private const val START = "### SPIRITUAL_DELTA ###"
    private const val END = "### END_SPIRITUAL_DELTA ###"

    private val scoreRe = Regex("""(?im)^\s*score\s*:\s*(-?\d+)\s*$""")
    private val qualityRe = Regex("""(?im)^\s*quality\s*:\s*(\S+)\s*$""")
    private val reasonRe = Regex("""(?im)^\s*reason\s*:\s*(.+)$""")

    /** Instruction block appended to analyze prompts. */
    const val PROMPT_BLOCK: String =
        "ОБЯЗАТЕЛЬНО после основного текста и после блока «Моя личность» (если он запрошен) " +
            "выведи один блок SPIRITUAL_DELTA в точном формате. Это последний блок ответа:\n" +
            "$START\n" +
            "score: <целое от -5 до 10>\n" +
            "quality: <high|ok|low|fictitious>\n" +
            "reason: <краткая причина на русском>\n" +
            "$END\n" +
            "Порядок конца ответа: текст → Моя личность → SPIRITUAL_DELTA.\n" +
            "Правила score: честность, осознанность, соответствие программе и глубина проработки повышают; " +
            "формальность, избегание, лень, отписки снижают. " +
            "quality=fictitious — явная отписка, копипаст или уход от темы; " +
            "low — поверхностно; ok — нормально; high — глубокая честная работа. " +
            "В основном тексте ответа этот блок не дублируй и не комментируй."

    fun parseAndStrip(text: String): SpiritualDelta? {
        val start = text.indexOf(START)
        val end = text.indexOf(END)
        if (start < 0 || end <= start) {
            return null
        }
        val body = text.substring(start + START.length, end).trim()
        val visible = (text.substring(0, start) + text.substring(end + END.length)).trim()
        val score = scoreRe.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return SpiritualDelta(0, SpiritualQuality.OK, "", visible)
        val quality = SpiritualQuality.parse(qualityRe.find(body)?.groupValues?.getOrNull(1))
        val reason = reasonRe.find(body)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            .ifBlank { SpiritualRu.aiDefaultReason }
        return SpiritualDelta(
            score = SpiritualEconomy.clampAiScore(score),
            quality = quality,
            reason = reason,
            visibleText = visible
        )
    }

    fun stripOnly(text: String): String {
        val start = text.indexOf(START)
        val end = text.indexOf(END)
        if (start < 0 || end <= start) return text
        return (text.substring(0, start) + text.substring(end + END.length)).trim()
    }
}
