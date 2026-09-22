package ru.na.step4.obidy.data.journal

import java.text.SimpleDateFormat
import java.util.Date
import org.json.JSONObject
import ru.na.step4.obidy.data.ai.AiHttp
import ru.na.step4.obidy.data.profile.PersonalityPortrait
import ru.na.step4.obidy.data.profile.ProfileProblems
import ru.na.step4.obidy.data.profile.ProfileQuestionnaire
import ru.na.step4.obidy.data.profile.ProfileStore

/** Объём ответа ИИ — настройка для «Литературы к вопросу» и «Рекомендаций для тебя». */
enum class AiLength(val key: String) {
    SHORT("short"),
    STANDARD("standard"),
    LONG("long");

    fun label(): String = when (this) {
        SHORT -> JournalRu.aiLengthShort
        STANDARD -> JournalRu.aiLengthStandard
        LONG -> JournalRu.aiLengthLong
    }

    fun instruction(): String = when (this) {
        SHORT -> "Объём ответа: короткий — до 150 слов, только суть, без вступлений и повторов."
        STANDARD -> "Объём ответа: стандарт — до 350 слов, по пунктам, без воды."
        LONG -> "Объём ответа: длинный — до 700 слов, подробно по каждому пункту."
    }

    fun maxTokens(): Int = when (this) {
        SHORT -> 2400
        STANDARD -> 4500
        LONG -> 8000
    }

    companion object {
        fun of(value: String?): AiLength =
            entries.firstOrNull { it.key == value?.trim()?.lowercase() } ?: STANDARD
    }
}

object JournalAiClient {
    sealed class Result {
        data class Ok(val text: String, val prompt: String = "") : Result()
        data class Err(val message: String) : Result()
    }

    fun chat(
        user: String,
        role: String,
        program: String = "",
        language: String = ru.na.step4.obidy.data.i18n.I18n.languageCode(),
        premium: Boolean = false,
        admin: Boolean = false,
        maxTokens: Int = 4000
    ): Result {
        val payload = JSONObject()
            .put("role", role)
            .put("program", program)
            .put("user", user)
            .put("language", language)
            .put("max_tokens", maxTokens.coerceIn(256, 8000))
            .put("premium", premium)
            .put("admin", admin)
        return when (val raw = AiHttp.post("/api/v1/chat", payload, readTimeoutMs = 180_000)) {
            is AiHttp.Result.Err -> Result.Err(raw.message)
            is AiHttp.Result.Ok -> parse(raw.code, raw.body)
        }
    }

    private fun parse(code: Int, raw: String): Result {
        val obj = AiHttp.parseObject(raw)
        if (code in 200..299) {
            val text = obj.optString("text").trim()
            val prompt = obj.optString("prompt").trim()
            return if (text.isBlank()) Result.Err(JournalRu.aiError) else Result.Ok(text, prompt)
        }
        return Result.Err(AiHttp.errorMessage(obj, JournalRu.aiError))
    }
}

object JournalPrompts {
    const val PERSONALITY_START = PersonalityPortrait.START
    const val PERSONALITY_END = PersonalityPortrait.END

    /** Лимит токенов для выбранного объёма ответа. */
    fun lengthMaxTokens(lengthKey: String): Int = AiLength.of(lengthKey).maxTokens()

    /**
     * «Литература к вопросу»: без имени, анкеты и личных данных —
     * только вопрос, текст точки и программа.
     */
    fun literatureUser(path: TreePath, program: String, lengthKey: String): String =
        pointOnlyUser(path, program, AiLength.of(lengthKey))

    /**
     * «Рекомендации для тебя»: без имени, анкеты и личных данных —
     * только вопрос, текст точки и программа.
     */
    fun adviceUser(path: TreePath, program: String, lengthKey: String): String =
        pointOnlyUser(path, program, AiLength.of(lengthKey))

    private fun pointOnlyUser(path: TreePath, program: String, length: AiLength): String {
        val node = path.current
        val selection = when (node.type) {
            NodeType.POINT -> "Точку \"${node.name}\""
            NodeType.CHAPTER -> "Главе \"${node.name}\""
            NodeType.STEP -> "Шаге \"${node.name}\""
        }
        val fullPath = listOfNotNull(path.step.name, path.chapter?.name, path.point?.name)
            .joinToString(" → ")
        return buildString {
            append("Точка: ")
            append(selection)
            append(" (Шаг: ${path.step.name}) (полный путь: $fullPath).\n")
            append("Текущая дата: ${nowStamp()}\n")
            program.trim().takeIf { it.isNotBlank() }?.let {
                append("\nПрограмма: ")
                append(it)
                append("\n")
            }
            append("\n")
            append(length.instruction())
        }
    }

    /** Помощь по конкретной записи пользователя. */
    fun helpEntryUser(
        path: TreePath,
        entry: JournalEntry,
        personality: String?,
        questionnaire: String?
    ): String {
        val date = SimpleDateFormat("dd.MM.yyyy", ru.na.step4.obidy.data.i18n.I18n.locale()).format(Date(entry.createdAt))
        return buildString {
            appendLine("Текущая дата: ${nowStamp()}")
            appendLine("Контекст: \"${path.current.name}\" (полный путь: ${path.line()}).")
            questionnaire?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("Данные из анкеты:")
                appendLine(it)
            }
            personality?.let {
                appendLine()
                appendLine("Моя личность:")
                appendLine(it)
            }
            appendLine()
            appendLine("Моя запись (от $date):")
            append(entry.text)
        }
    }

    /** Оценка соответствия примеров вопросу: по точке (несколько записей) или по одной записи. */
    fun analyzeUser(
        path: TreePath,
        entries: List<JournalEntry>,
        personality: String?,
        questionnaire: String?,
        singleEntry: Boolean,
        collectPersonality: Boolean = false
    ): String {
        val node = path.current
        val questionText = if (node.type == NodeType.POINT) {
            "Точку \"${node.name}\""
        } else {
            "\"${node.name}\""
        }
        val dateFmt = SimpleDateFormat("dd.MM.yyyy", ru.na.step4.obidy.data.i18n.I18n.locale())
        return buildString {
            appendLine("Текущая дата: ${nowStamp()}")
            append("Вопрос: $questionText")
            append(" (Шаг: ${path.step.displayTitle()})")
            appendLine()
            appendLine("Полный путь: ${path.line()}")
            questionnaire?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("Данные из анкеты:")
                appendLine(it)
            }
            personality?.let {
                appendLine()
                appendLine("Моя личность:")
                appendLine(it)
            }
            appendLine()
            appendLine("Мои примеры/записи по этому вопросу:")
            entries.sortedBy { it.createdAt }.forEachIndexed { index, entry ->
                val date = dateFmt.format(Date(entry.createdAt))
                if (singleEntry) {
                    appendLine("Запись (от $date):")
                } else {
                    appendLine("Запись ${index + 1} (от $date):")
                }
                appendLine(entry.text)
                appendLine()
            }
        }
    }

    private fun nowStamp(): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", ru.na.step4.obidy.data.i18n.I18n.locale()).format(Date())

    fun parsePersonality(response: String): Pair<String, String?> =
        PersonalityPortrait.parse(response)

    fun formatQuestionnaire(profile: ProfileStore): String? =
        ProfileQuestionnaire.formatAnswers(profile.current)

    fun formatProblems(keys: Set<String>): String? = ProfileProblems.labels(keys)
}
