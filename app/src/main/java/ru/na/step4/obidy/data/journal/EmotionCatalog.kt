package ru.na.step4.obidy.data.journal

data class WordColumn(
    val id: String,
    val title: String,
    val words: List<String>
)

object EmotionCatalog {
    val feelingColumns = listOf(
        WordColumn(
            "anger",
            "Гнев",
            listOf(
                "Бешенство", "Ярость", "Ненависть", "Истерия", "Злость", "Раздражение",
                "Презрение", "Негодование", "Обида", "Ревность", "Уязвлённость", "Досада",
                "Зависть", "Неприязнь", "Возмущение", "Отвращение"
            )
        ),
        WordColumn(
            "fear",
            "Страх",
            listOf(
                "Ужас", "Отчаяние", "Испуг", "Оцепенение", "Подозрение", "Тревога",
                "Ошарашенность", "Беспокойство", "Боязнь", "Унижение", "Замешательство",
                "Растерянность", "Вина", "Стыд", "Сомнение", "Застенчивость", "Опасение",
                "Смущение", "Сломленность", "Подвох", "Надменность", "Ошеломлённость"
            )
        ),
        WordColumn(
            "sadness",
            "Грусть",
            listOf(
                "Горечь", "Тоска", "Скорбь", "Лень", "Жалость", "Отрешённость", "Отчаяние",
                "Беспомощность", "Душевная боль", "Безнадёжность", "Отчуждённость",
                "Разочарование", "Потрясение", "Сожаление", "Скука", "Безысходность",
                "Печаль", "Загнанность"
            )
        ),
        WordColumn(
            "joy",
            "Радость",
            listOf(
                "Счастье", "Восторг", "Ликование", "Приподнятость", "Оживление",
                "Умиротворение", "Увлечение", "Интерес", "Забота", "Ожидание", "Возбуждение",
                "Предвкушение", "Надежда", "Любопытство", "Освобождение", "Принятие",
                "Нетерпение", "Вера", "Изумление"
            )
        ),
        WordColumn(
            "love",
            "Любовь",
            listOf(
                "Нежность", "Теплота", "Сочувствие", "Блаженство", "Доверие", "Безопасность",
                "Благодарность", "Спокойствие", "Симпатия", "Идентичность", "Гордость",
                "Восхищение", "Уважение", "Самоценность", "Влюблённость", "Любовь к себе",
                "Очарованность", "Смирение", "Искренность", "Дружелюбие", "Доброта",
                "Взаимовыручка"
            )
        )
    )

    val thoughtColumns = listOf(
        WordColumn(
            "anger",
            "Гнев",
            listOf(
                "Нервозность", "Пренебрежение", "Недовольство", "Вредность", "Огорчение",
                "Нетерпимость", "Вседозволенность"
            )
        ),
        WordColumn(
            "fear",
            "Страх",
            listOf(
                "Раскаяние", "Безвыходность", "Превосходство", "Высокомерие", "Неполноценность",
                "Неудобство", "Неловкость", "Апатия / безразличие", "Неуверенность"
            )
        ),
        WordColumn(
            "sadness",
            "Грусть",
            listOf(
                "Тупик", "Усталость", "Принуждение", "Одиночество", "Отверженность",
                "Подавленность", "Холодность", "Безучастность", "Равнодушие"
            )
        ),
        WordColumn(
            "joy",
            "Радость",
            listOf(
                "Удовлетворение", "Уверенность", "Довольство", "Окрылённость", "Торжественность",
                "Жизнерадостность", "Облегчение", "Ободрённость", "Удивление"
            )
        ),
        WordColumn(
            "love",
            "Любовь",
            listOf(
                "Сопереживание", "Сопричастность", "Уравновешенность", "Смирение",
                "Естественность", "Жизнелюбие", "Вдохновение", "Воодушевление"
            )
        )
    )

    fun columns(kind: JournalFieldKind): List<WordColumn> = when (kind) {
        JournalFieldKind.FEELINGS -> feelingColumns
        JournalFieldKind.THOUGHTS -> thoughtColumns
        JournalFieldKind.TEXT -> emptyList()
    }

    fun allWords(kind: JournalFieldKind): List<Pair<WordColumn, String>> =
        columns(kind).flatMap { column -> column.words.map { column to it } }

    fun containsWord(
        text: String,
        word: String,
        kind: JournalFieldKind = JournalFieldKind.FEELINGS
    ): Boolean {
        if (text.isBlank() || word.isBlank()) return false
        val matches = wordBoundary(word).findAll(text).toList()
        if (matches.isEmpty()) return false
        val longer = columns(kind).flatMap { it.words }
            .filter { it.length > word.length && it.contains(word, ignoreCase = true) }
        if (longer.isEmpty()) return true
        return matches.any { match ->
            longer.none { longWord ->
                wordBoundary(longWord).findAll(text).any { longMatch ->
                    match.range.first >= longMatch.range.first &&
                        match.range.last <= longMatch.range.last
                }
            }
        }
    }

    fun selectedWords(text: String, kind: JournalFieldKind = JournalFieldKind.FEELINGS): List<String> =
        columns(kind).flatMap { it.words }.filter { containsWord(text, it, kind) }

    /**
     * Appends "Word - " on a new line (capitalized) for dictation after the dash.
     * If the word is already present, removes its line / occurrence instead.
     * @return Pair(newText, startDictation)
     */
    fun pickWordForDictate(
        text: String,
        word: String,
        kind: JournalFieldKind = JournalFieldKind.FEELINGS
    ): Pair<String, Boolean> {
        val trimmedWord = word.trim()
        if (trimmedWord.isBlank()) return text to false
        if (containsWord(text, trimmedWord, kind)) {
            return removeWordOccurrence(text, trimmedWord, kind) to false
        }
        return appendWordLine(text, trimmedWord) to true
    }

    fun appendWordLine(text: String, word: String): String {
        val label = word.trim().replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(java.util.Locale.getDefault()) else ch.toString()
        }
        if (label.isEmpty()) return text
        val line = "$label - "
        val trimmed = text.trimEnd()
        return if (trimmed.isEmpty()) line else "$trimmed\n$line"
    }

    fun toggleWord(text: String, word: String, kind: JournalFieldKind = JournalFieldKind.FEELINGS): String =
        pickWordForDictate(text, word, kind).first

    private fun removeWordOccurrence(
        text: String,
        word: String,
        kind: JournalFieldKind
    ): String {
        val lineRegex = Regex(
            """(?im)^[ \t]*${Regex.escape(word)}[ \t]*-[ \t]*.*(?:\r?\n)?"""
        )
        var next = lineRegex.replace(text, "")
        if (containsWord(next, word, kind)) {
            next = next.replace(wordBoundary(word), "")
                .replace(Regex("""\s*,\s*,"""), ",")
                .replace(Regex("""^\s*,\s*"""), "")
                .replace(Regex(""",\s*$"""), "")
        }
        return next.replace(Regex("""\n{3,}"""), "\n\n").trim()
    }

    private fun wordBoundary(word: String) =
        Regex("(?i)(?<![А-Яа-яA-Za-z])${Regex.escape(word)}(?![А-Яа-яA-Za-z])")
}
