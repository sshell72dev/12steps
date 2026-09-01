package ru.na.step4.obidy.data.journal

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class JournalFieldKind {
    TEXT,
    THOUGHTS,
    FEELINGS
}

data class JournalFieldSpec(
    val id: String,
    val title: String,
    val kind: JournalFieldKind
)

object JournalFields {
    const val ID_SITUATION = "situation"
    const val ID_THOUGHTS = "thoughts"
    const val ID_FEELINGS = "feelings"
    const val ID_ACTIONS = "actions"

    val defaults = listOf(
        JournalFieldSpec(ID_SITUATION, "Ситуация", JournalFieldKind.TEXT),
        JournalFieldSpec(ID_THOUGHTS, "Мысли", JournalFieldKind.THOUGHTS),
        JournalFieldSpec(ID_FEELINGS, "Чувства", JournalFieldKind.FEELINGS),
        JournalFieldSpec(ID_ACTIONS, "Действия", JournalFieldKind.TEXT)
    )

    fun kindFromTitle(title: String, fallback: JournalFieldKind = JournalFieldKind.TEXT): JournalFieldKind =
        when (title.trim().lowercase()) {
            "мысли" -> JournalFieldKind.THOUGHTS
            "чувства" -> JournalFieldKind.FEELINGS
            else -> fallback
        }

    fun newField(title: String, kind: JournalFieldKind): JournalFieldSpec {
        val trimmed = title.trim().ifBlank { "Поле" }
        return JournalFieldSpec(
            id = UUID.randomUUID().toString(),
            title = trimmed,
            kind = kindFromTitle(trimmed, kind)
        )
    }

    fun format(fields: List<JournalFieldSpec>, values: Map<String, String>): String =
        fields.mapNotNull { field ->
            val text = values[field.id].orEmpty().trim()
            if (text.isBlank()) null else "${field.title}:\n$text"
        }.joinToString("\n\n")

    fun parse(text: String, fields: List<JournalFieldSpec>): Map<String, String> {
        val trimmed = text.trim()
        if (trimmed.isBlank() || fields.isEmpty()) return emptyMap()
        val byTitle = fields.associateBy { it.title.trim().lowercase() }
        val values = linkedMapOf<String, StringBuilder>()
        var currentId: String? = null
        val leftover = StringBuilder()

        trimmed.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            val header = matchHeader(line, byTitle)
            if (header != null) {
                currentId = header.first.id
                values.getOrPut(header.first.id) { StringBuilder() }
                if (header.second.isNotBlank()) {
                    appendLine(values.getValue(header.first.id), header.second)
                }
            } else if (currentId != null) {
                appendLine(values.getValue(currentId!!), line)
            } else if (line.isNotBlank() || leftover.isNotEmpty()) {
                appendLine(leftover, line)
            }
        }

        val result = values.mapValues { it.value.toString().trim() }.filterValues { it.isNotBlank() }
            .toMutableMap()
        val extra = leftover.toString().trim()
        if (extra.isNotBlank() && result.isEmpty()) {
            result[fields.first().id] = extra
        }
        return result
    }

    fun looksStructured(text: String, fields: List<JournalFieldSpec>): Boolean {
        if (fields.isEmpty()) return false
        val titles = fields.map { "${it.title.trim().lowercase()}:" }
        return text.lineSequence().any { line ->
            val lower = line.trim().lowercase()
            titles.any { title -> lower == title || lower.startsWith(title) }
        }
    }

    fun selectedWords(value: String): List<String> =
        value.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    fun toggleWord(value: String, word: String): String {
        val current = selectedWords(value).toMutableList()
        val match = current.indexOfFirst { it.equals(word, ignoreCase = true) }
        if (match >= 0) current.removeAt(match) else current.add(word)
        return current.joinToString(", ")
    }

    fun encodeFields(fields: List<JournalFieldSpec>): String {
        val arr = JSONArray()
        fields.forEach { field ->
            arr.put(
                JSONObject()
                    .put("id", field.id)
                    .put("title", field.title)
                    .put("kind", field.kind.name)
            )
        }
        return arr.toString()
    }

    fun decodeFields(raw: String?): List<JournalFieldSpec> {
        if (raw.isNullOrBlank()) return defaults
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val kind = runCatching {
                        JournalFieldKind.valueOf(obj.optString("kind"))
                    }.getOrDefault(JournalFieldKind.TEXT)
                    val title = obj.optString("title").trim()
                    if (title.isBlank()) continue
                    add(
                        JournalFieldSpec(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            title = title,
                            kind = kind
                        )
                    )
                }
            }.ifEmpty { defaults }
        }.getOrDefault(defaults)
    }

    fun encodeValues(values: Map<String, String>): String {
        val obj = JSONObject()
        values.forEach { (id, text) ->
            if (text.isNotBlank()) obj.put(id, text)
        }
        return obj.toString()
    }

    fun decodeValues(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    val value = obj.optString(key)
                    if (value.isNotBlank()) put(key, value)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun matchHeader(
        line: String,
        byTitle: Map<String, JournalFieldSpec>
    ): Pair<JournalFieldSpec, String>? {
        val trimmed = line.trim()
        if (!trimmed.contains(':')) return null
        val title = trimmed.substringBefore(':').trim()
        val rest = trimmed.substringAfter(':').trim()
        val field = byTitle[title.lowercase()] ?: return null
        return field to rest
    }

    private fun appendLine(target: StringBuilder, line: String) {
        if (target.isNotEmpty()) target.append('\n')
        target.append(line)
    }
}
