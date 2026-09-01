package ru.na.step4.obidy.data.analysis

import ru.na.step4.obidy.data.profile.PersonalityPortrait
import ru.na.step4.obidy.data.spiritual.SpiritualDeltaParser

object ReflectionQuestions {
    private val itemPrefix = Regex("^(?:[-*•–—]\\s+|\\d+[.)]\\s+|\\(\\d+\\)\\s+)")
    private val knownSections = listOf(
        "сводная оценка",
        "сильные стороны",
        "слепые зоны",
        "рекомендации",
        "практические рекомендации",
    )

    /** Strip service blocks before parsing or showing AI review text. */
    fun cleanReviewSource(text: String): String =
        SpiritualDeltaParser.stripOnly(PersonalityPortrait.strip(text))

    fun extract(text: String): List<String> {
        val lines = cleanReviewSource(text).replace("\r\n", "\n").replace('\r', '\n').lines()
        val start = lines.indexOfFirst { isReflectionHeading(it) }
        if (start >= 0) {
            val body = lines.drop(start + 1).takeWhile { !isSectionStop(it) }
            val fromSection = parseItems(body)
            if (fromSection.isNotEmpty()) return fromSection
        }
        return parseItems(lines.filter { it.contains('?') }).take(8)
    }

    private fun parseItems(body: List<String>): List<String> {
        val chunks = mutableListOf<String>()
        for (raw in body) {
            if (isSectionStop(raw)) break
            val trimmed = raw.trim().replace("**", "").trim()
            if (trimmed.isBlank()) continue
            val numbered = itemPrefix.containsMatchIn(trimmed)
            val stripped = itemPrefix.replaceFirst(trimmed, "").trim()
            if (stripped.isBlank() || !isReflectionQuestion(stripped)) continue
            if (chunks.isEmpty() || numbered || chunks.last().contains('?')) {
                chunks += stripped
            } else {
                chunks[chunks.lastIndex] = chunks.last() + " " + stripped
            }
        }
        val out = mutableListOf<String>()
        for (chunk in chunks) {
            if (!isReflectionQuestion(chunk)) continue
            val parts = chunk.split('?').map { it.trim() }.filter { it.length > 8 }
            if (parts.isEmpty()) continue
            if (chunk.contains('?')) {
                parts.forEach { part ->
                    val q = itemPrefix.replaceFirst(part, "").trim().trimStart('-', '*', '•', ' ')
                    if (q.length > 8 && isReflectionQuestion(q)) {
                        out += if (q.endsWith('?')) q else "$q?"
                    }
                }
            } else if (chunk.length > 12) {
                out += chunk
            }
        }
        return out.distinct()
    }

    private fun normalize(line: String): String {
        var t = line.trim().replace("**", "").trimStart('#', '*', ' ')
        t = t.replace(Regex("^\\d+[.)]\\s+"), "")
        return t.trimEnd('*', ' ', ':').lowercase()
    }

    private fun isReflectionHeading(line: String): Boolean {
        val t = normalize(line)
        return t.contains("вопрос") && t.contains("рефлекси") && t.length < 100
    }

    private fun isKnownSectionHeading(line: String): Boolean {
        val t = normalize(line)
        return knownSections.any { t == it || t.startsWith("$it ") || t.startsWith("$it—") }
    }

    private fun isMetaSectionHeading(line: String): Boolean {
        val t = normalize(line)
        if (t.contains("моя личность") || t.contains("my personality")) return true
        if (t.contains("spiritual_delta") || t.contains("end_spiritual_delta")) return true
        val raw = line.trim()
        return raw.contains("МОЯ_ЛИЧНОСТЬ", ignoreCase = true) ||
            raw.contains("КОНЕЦ_МОЯ_ЛИЧНОСТЬ", ignoreCase = true)
    }

    private fun isSectionStop(line: String): Boolean =
        isKnownSectionHeading(line) || isMetaSectionHeading(line)

    private fun isReflectionQuestion(candidate: String): Boolean {
        if (isMetaSectionHeading(candidate)) return false
        val t = normalize(candidate)
        if (t.contains("моя личность") || t.contains("my personality")) return false
        if (t.contains("spiritual_delta")) return false
        if (candidate.contains("МОЯ_ЛИЧНОСТЬ", ignoreCase = true)) return false
        if (candidate.contains("КОНЕЦ_МОЯ_ЛИЧНОСТЬ", ignoreCase = true)) return false
        return true
    }
}
