package ru.na.step4.obidy.data

/**
 * Local type suggester for a situation description.
 * Ranks existing types and proposes new labels from presets + keyword cues.
 */
object TypeSuggestEngine {

    data class Result(
        /** Existing types for this resentment, A–Z. Matching ones listed first within the block. */
        val existing: List<SituationType>,
        /** New type names not yet on the resentment (AI / preset suggestions). */
        val proposed: List<String>
    )

    private val cueMap: List<Pair<List<String>, String>> = listOf(
        listOf("критик", "униж", "оскорб", "высме", "стыд", "обесцен") to "Критика / унижение",
        listOf("предат", "измен", "обману", "врёт", "врал", "лгал") to "Предательство",
        listOf("игнор", "молчит", "не отвечает", "отверг", "брос") to "Игнор",
        listOf("контрол", "запрещ", "давлени", "команд", "диктов") to "Контроль",
        listOf("обещан", "не сдержал", "нарушил слово", "кинул") to "Невыполненные обещания",
        listOf("удар", "угроз", "насили", "кричал", "бил", "страшн") to "Насилие / угроза",
        listOf("ожидан", "разочаров", "должен был", "хотел чтобы") to "Разочарование в ожиданиях",
        listOf("ревност", "ревну") to "Ревность",
        listOf("отвержен", "не любят", "не любят меня") to "Отвержение",
        listOf("несправед", "нечестн", "двойные стандарт") to "Несправедливость",
        listOf("отказ", "не дал", "отказал") to "Отказ / отказ в помощи",
        listOf("сравнен", "лучше чем я", "хуже меня") to "Сравнение",
        listOf("граница", "вторг", "личн") to "Нарушение границ",
        listOf("деньг", "долг", "финанс", "зарплат") to "Деньги / долги",
        listOf("работ", "начальник", "коллег", "увольн") to "Работа / коллеги",
        listOf("семь", "мама", "папа", "родител", "брат", "сестр") to "Семья",
    )

    fun suggest(text: String, existing: List<SituationType>): Result {
        val hay = text.lowercase()
        val existingSorted = existing.sortedWith(
            compareByDescending<SituationType> { type -> scoreName(hay, type.name) > 0 }
                .thenBy { it.name.lowercase() }
        )

        val existingNames = existing.map { it.name.lowercase() }.toSet()
        val scored = linkedMapOf<String, Int>()

        fun addCandidate(name: String, bonus: Int) {
            val key = name.trim()
            if (key.isEmpty()) return
            if (key.lowercase() in existingNames) return
            scored[key] = maxOf(scored[key] ?: 0, bonus)
        }

        InventoryStructure.suggestedSituationTypes.forEach { preset ->
            val s = scoreName(hay, preset)
            if (s > 0 || hay.isBlank()) addCandidate(preset, s + 1)
        }

        cueMap.forEach { (cues, label) ->
            if (cues.any { hay.contains(it) }) addCandidate(label, 10)
        }

        // Phrase-derived short labels from significant words
        tokenize(hay).filter { it.length >= 5 }.take(4).forEach { token ->
            val titled = token.replaceFirstChar { it.uppercase() }
            addCandidate(titled, 2)
        }

        val proposed = scored.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .map { it.key }
            .distinct()
            .take(12)

        return Result(existing = existingSorted, proposed = proposed)
    }

    fun filterCatalog(query: String, names: List<String>): List<String> {
        val sorted = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .sortedBy { it.lowercase() }
        if (query.isBlank()) return sorted
        val q = query.trim().lowercase()
        return sorted.filter { it.lowercase().contains(q) }
    }

    fun catalogNames(existing: List<SituationType>): List<String> {
        val fromDb = existing.map { it.name }
        val presets = InventoryStructure.suggestedSituationTypes
        return (fromDb + presets).distinct()
    }

    private fun scoreName(hay: String, name: String): Int {
        if (hay.isBlank()) return 0
        val n = name.lowercase()
        var score = 0
        if (hay.contains(n)) score += 8
        tokenize(n).forEach { part ->
            if (part.length >= 3 && hay.contains(part)) score += 3
        }
        tokenize(hay).forEach { part ->
            if (part.length >= 4 && n.contains(part)) score += 2
        }
        return score
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }
}
