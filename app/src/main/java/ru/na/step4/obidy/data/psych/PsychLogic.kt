package ru.na.step4.obidy.data.psych

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

object PsychLogic {
    private val readiness = setOf(
        "готов", "готова", "я готов", "я готова", "готов писать", "готова писать",
        "ok", "okay", "ок", "окей", "да", "yes", "ага", "угу", "ready",
        "i am ready", "im ready", "i'm ready", "go", "поехали", "давай",
        "пишу", "отправляю", "сейчас напишу"
    )

    private val blindLine = Regex("(?im)^[ \\t]*(?:#{1,6}\\s*|\\*\\*|__)?\\s*Слепые зоны\\b.*$")
    private val blindAny = Regex("(?i)Слепые зоны")

    fun looksLikeReadiness(text: String): Boolean {
        val raw = text.trim()
        if (raw.isEmpty() || raw.length > 48) return false
        val normalized = raw.lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized in readiness) return true
        return (normalized.startsWith("я готов") || normalized.startsWith("я готова")) &&
            normalized.length <= 24
    }

    fun looksLikeSituationName(text: String): Boolean = text.trim().length > 40

    fun splitTeaser(text: String): Pair<String, String?> {
        val raw = text.trim()
        if (raw.isEmpty()) return "" to null
        val line = blindLine.find(raw)
        val cut = when {
            line != null -> line.range.first
            else -> {
                val any = blindAny.find(raw) ?: return raw to null
                any.range.first
            }
        }
        val visible = raw.substring(0, cut).trimEnd()
        return if (visible.isBlank()) raw to null else visible to raw
    }

    fun encodeQuestions(items: List<String>): String {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        return arr.toString()
    }

    fun decodeQuestions(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun cacheKey(situationId: Long, lang: String, kind: String, suffix: String): String {
        val raw = "situation:$situationId:$lang:$kind:$suffix"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun dialogueSuffix(answers: List<PsychQa>): String =
        answers.joinToString("|") { "${it.question}\n${it.answer}" }

    fun startOfTodayUtc(offsetMinutes: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.MINUTE, offsetMinutes)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MINUTE, -offsetMinutes)
        return cal.timeInMillis
    }

    fun dayRange(dateMillis: Long, offsetMinutes: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = dateMillis
        cal.add(Calendar.MINUTE, offsetMinutes)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startLocal = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val endLocal = cal.timeInMillis
        return (startLocal - offsetMinutes * 60_000L) to (endLocal - offsetMinutes * 60_000L)
    }

    fun weekRange(now: Long, offsetMinutes: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = now
        cal.add(Calendar.MINUTE, offsetMinutes)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val day = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        cal.add(Calendar.DAY_OF_MONTH, -day)
        val startLocal = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 7)
        val endLocal = cal.timeInMillis
        return (startLocal - offsetMinutes * 60_000L) to (endLocal - offsetMinutes * 60_000L)
    }

    fun shortStory(text: String, maxChars: Int = 180): String {
        val one = text.trim().replace(Regex("\\s+"), " ")
        if (one.isEmpty()) return ""
        if (one.length <= maxChars) return one
        return one.take(maxChars - 1).trimEnd(' ', ',', ';', '.', '—', '-') + "…"
    }

    fun formatLocal(millis: Long, offsetMinutes: Int): String {
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", ru.na.step4.obidy.data.i18n.I18n.locale())
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(millis + offsetMinutes * 60_000L))
    }

    fun parseDateInput(raw: String, now: Long, offsetMinutes: Int): Long? {
        val text = raw.trim()
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = now
        cal.add(Calendar.MINUTE, offsetMinutes)
        val yearNow = cal.get(Calendar.YEAR)
        val parts = text.split(".", "-", "/", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year = when {
            parts.size >= 3 && parts[2].length == 4 -> parts[2].toIntOrNull() ?: yearNow
            parts.size >= 3 && parts[2].length == 2 -> 2000 + (parts[2].toIntOrNull() ?: 0)
            else -> yearNow
        }
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis - offsetMinutes * 60_000L
    }

    fun inQuietHours(now: Long, offsetMinutes: Int, startHour: Int, endHour: Int): Boolean {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = now + offsetMinutes * 60_000L
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return if (startHour <= endHour) {
            hour in startHour until endHour
        } else {
            hour >= startHour || hour < endHour
        }
    }

    fun shouldUpsell(usedToday: Int, limit: Int): Boolean {
        if (limit <= 0) return usedToday == 1
        return usedToday == 1 || usedToday % 5 == 0 || (limit - usedToday) <= 2
    }

    fun shareText(
        situation: String,
        answers: List<PsychQa>,
        createdAt: Long,
        offsetMinutes: Int
    ): String = buildString {
        appendLine("🧩 Ситуация по дню")
        appendLine()
        appendLine(situation.trim())
        appendLine()
        appendLine("❓ Вопросы и ответы")
        answers.forEachIndexed { i, qa ->
            appendLine("${i + 1}. ${qa.question}")
            appendLine("Ответ: ${qa.answer}")
            appendLine()
        }
        append("Дата и время: ${formatLocal(createdAt, offsetMinutes)}")
    }

    fun profileJson(settings: PsychSettings): JSONObject {
        val o = JSONObject()
        settings.profileMap().forEach { (k, v) ->
            when (v) {
                null -> o.put(k, "")
                is Boolean -> o.put(k, v)
                is Int -> o.put(k, v)
                else -> o.put(k, v.toString())
            }
        }
        return o
    }
}

object PsychLocks {
    private val until = ConcurrentHashMap<String, Long>()

    fun tryLock(key: String, ms: Long = PsychSettings.LOCK_MS): Boolean {
        val now = System.currentTimeMillis()
        val current = until[key] ?: 0L
        if (current > now) return false
        until[key] = now + ms
        return true
    }

    fun unlock(key: String) {
        until.remove(key)
    }
}

data class TeaserCacheEntry(
    val fullText: String,
    val speakable: String,
    val createdAt: Long = System.currentTimeMillis()
)

object PsychTeaserStore {
    private val items = ConcurrentHashMap<String, TeaserCacheEntry>()

    fun put(full: String, speakable: String): String {
        prune()
        val key = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        items[key] = TeaserCacheEntry(full, speakable)
        return key
    }

    fun get(key: String): TeaserCacheEntry? {
        prune()
        return items[key]
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - PsychSettings.TEASER_TTL_MS
        items.entries.removeIf { it.value.createdAt < cutoff }
    }
}
