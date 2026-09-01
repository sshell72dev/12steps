package ru.na.step4.obidy.data

import org.json.JSONObject

enum class InventoryInsightKind {
    DRAFT,
    BLIND_SPOT
}

data class InventoryFieldInsight(
    val key: String,
    val title: String,
    val kind: InventoryInsightKind,
    val text: String
)

object InventoryAi {
    fun workThroughUserPrompt(
        target: String,
        typeNames: List<String>,
        situation: Situation,
        emptyKeys: List<String>,
        filledKeys: List<String>,
        personality: String?,
        questionnaire: String?
    ): String {
        val filled = QuestionFocus.buildSituationAnswersText(target, situation, typeNames)
        return buildString {
            questionnaire?.takeIf { it.isNotBlank() }?.let {
                appendLine("Анкета:")
                appendLine(it)
                appendLine()
            }
            personality?.takeIf { it.isNotBlank() && it != "(пока не заполнено)" }?.let {
                appendLine("Моя личность:")
                appendLine(it)
                appendLine()
            }
            appendLine("Уже написано:")
            appendLine(filled)
            appendLine()
            if (emptyKeys.isNotEmpty()) {
                appendLine("Пустые вопросы — нужен черновик ответа от первого лица («я») по каждому:")
                emptyKeys.forEach { key ->
                    appendLine("- [[$key]] ${QuestionFocus.titleOf(key)}")
                }
                appendLine()
            }
            if (filledKeys.isNotEmpty()) {
                appendLine("Уже заполненные вопросы — слепые зоны:")
                filledKeys.forEach { key ->
                    appendLine("- [[blind:$key]] ${QuestionFocus.titleOf(key)}")
                }
                appendLine()
            }
        }
    }

    fun fullAnalysisUserPrompt(
        target: String,
        typeNames: List<String>,
        situation: Situation,
        personality: String?,
        questionnaire: String?
    ): String {
        val filled = QuestionFocus.buildSituationAnswersText(target, situation, typeNames)
        return buildString {
            questionnaire?.takeIf { it.isNotBlank() }?.let {
                appendLine("Анкета:")
                appendLine(it)
                appendLine()
            }
            personality?.takeIf { it.isNotBlank() && it != "(пока не заполнено)" }?.let {
                appendLine("Моя личность:")
                appendLine(it)
                appendLine()
            }
            appendLine("Ситуация обиды:")
            appendLine(filled)
        }
    }

    fun parseInsights(
        raw: String,
        emptyKeys: Set<String>,
        filledKeys: Set<String>
    ): List<InventoryFieldInsight> {
        val fromMarkers = parseMarkers(raw, emptyKeys, filledKeys)
        if (fromMarkers.isNotEmpty()) return fromMarkers
        return parseJson(raw, emptyKeys, filledKeys)
    }

    private fun parseMarkers(
        raw: String,
        emptyKeys: Set<String>,
        filledKeys: Set<String>
    ): List<InventoryFieldInsight> {
        val matches = Regex(
            """\[\[(blind:)?([a-z0-9]+)]]""",
            RegexOption.IGNORE_CASE
        ).findAll(raw).toList()
        if (matches.isEmpty()) return emptyList()
        val out = ArrayList<InventoryFieldInsight>()
        matches.forEachIndexed { index, match ->
            val isBlind = match.groupValues[1].isNotBlank()
            val key = match.groupValues[2].lowercase()
            val kind = if (isBlind) InventoryInsightKind.BLIND_SPOT else InventoryInsightKind.DRAFT
            val allowed = if (isBlind) filledKeys else emptyKeys
            if (key !in allowed) return@forEachIndexed
            val start = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: raw.length
            val text = raw.substring(start, end).trim()
                .trim { it == '-' || it == '*' || it == '#' }
                .trim()
            if (text.isNotBlank()) {
                out += InventoryFieldInsight(key, QuestionFocus.titleOf(key), kind, text)
            }
        }
        return out
    }

    private fun parseJson(
        raw: String,
        emptyKeys: Set<String>,
        filledKeys: Set<String>
    ): List<InventoryFieldInsight> {
        val obj = extractJson(raw) ?: return emptyList()
        val drafts = obj.optJSONObject("suggestions") ?: obj.optJSONObject("drafts") ?: obj
        val blinds = obj.optJSONObject("blind_spots") ?: obj.optJSONObject("blinds")
        val out = ArrayList<InventoryFieldInsight>()
        emptyKeys.forEach { key ->
            val text = drafts.optString(key).trim()
            if (text.isNotBlank()) {
                out += InventoryFieldInsight(key, QuestionFocus.titleOf(key), InventoryInsightKind.DRAFT, text)
            }
        }
        filledKeys.forEach { key ->
            val text = blinds?.optString(key)?.trim().orEmpty()
            if (text.isNotBlank()) {
                out += InventoryFieldInsight(key, QuestionFocus.titleOf(key), InventoryInsightKind.BLIND_SPOT, text)
            }
        }
        return out
    }

    private fun extractJson(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(raw.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }

}
