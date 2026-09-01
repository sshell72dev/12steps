package ru.na.step4.obidy.data.profile

/**
 * Shared markers and parser for «Моя личность».
 * Accepts both ---МОЯ_ЛИЧНОСТЬ--- (journal) and ### МОЯ_ЛИЧНОСТЬ ### (psych).
 */
object PersonalityPortrait {
    const val START = "---МОЯ_ЛИЧНОСТЬ---"
    const val END = "---КОНЕЦ_МОЯ_ЛИЧНОСТЬ---"

    val PROMPT_BLOCK: String =
        "После основного текста для пользователя " +
            "и ОБЯЗАТЕЛЬНО ПЕРЕД блоком SPIRITUAL_DELTA, если он запрошен, выведи один блок " +
            "в точном формате (маркеры не меняй):\n" +
            "$START\n" +
            "<полный обновлённый портрет «Моя личность»: черты, ценности, реакции, ресурсы и зоны роста>\n" +
            "$END\n" +
            "В блоке только портрет, без оценки и рекомендаций. " +
            "Если выше уже есть портрет — сохрани существенное и дополни новым из этой записи. " +
            "Не копируй дословно ситуацию и не дублируй анкету."

    private val blockRe = Regex(
        """(?:#{3}|-{3})\s*МОЯ[_\s]*ЛИЧНОСТЬ\s*(?:#{3}|-{3})\s*(.*?)\s*(?:#{3}|-{3})\s*КОНЕЦ[_\s]*МОЯ[_\s]*ЛИЧНОСТЬ\s*(?:#{3}|-{3})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun parse(text: String): Pair<String, String?> {
        val match = blockRe.find(text) ?: return text.trim() to null
        val portrait = match.groupValues[1].trim().ifBlank { null }
        val visible = text.replace(blockRe, "").trim()
        return visible to portrait
    }

    fun strip(text: String): String = parse(text).first
}
