package ru.na.step4.obidy.data.spiritual

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SpiritualRatingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _snapshot = MutableStateFlow(readSnapshot())
    val snapshot: StateFlow<SpiritualSnapshot> = _snapshot.asStateFlow()

    fun refreshMissPenalties() {
        ensureDayBucket()
        val today = LocalDate.now()
        val lastActive = lastActiveDate() ?: run {
            publish()
            return
        }
        val yesterday = today.minusDays(1)
        if (lastActive >= today) {
            prefs.edit().putInt(KEY_MISS_STREAK, 0).apply()
            publish()
            return
        }
        var cursor = (lastPenaltyDate()?.plusDays(1) ?: lastActive.plusDays(1))
        if (cursor.isAfter(yesterday)) {
            val miss = ChronoUnit.DAYS.between(lastActive, today).toInt().coerceAtLeast(0)
            prefs.edit().putInt(KEY_MISS_STREAK, miss).apply()
            publish()
            return
        }
        var missStreak = prefs.getInt(KEY_MISS_STREAK, 0)
        var total = prefs.getInt(KEY_TOTAL, 0)
        var rate = rateValue()
        val events = eventsMutable()
        var addedPenalty = false
        while (!cursor.isAfter(yesterday)) {
            missStreak += 1
            val delta = SpiritualEconomy.penaltyForMissDay(missStreak)
            val id = "miss-$cursor"
            if (events.none { it.id == id }) {
                total = SpiritualEconomy.clampTotal(total + delta)
                events.add(
                    0,
                    SpiritualEvent(
                        id = id,
                        source = SpiritualSource.MISS,
                        delta = delta,
                        reason = SpiritualRu.reasonMiss,
                        at = System.currentTimeMillis()
                    )
                )
                addedPenalty = true
            }
            cursor = cursor.plusDays(1)
        }
        if (addedPenalty && missStreak >= 2) {
            rate = SpiritualEconomy.rateAfterMissStreak(rate, missStreak)
        }
        while (events.size > MAX_EVENTS) events.removeAt(events.lastIndex)
        prefs.edit()
            .putInt(KEY_TOTAL, total)
            .putInt(KEY_MISS_STREAK, missStreak)
            .putString(KEY_LAST_PENALTY, yesterday.toString())
            .putInt(KEY_RATE, toRateInt(rate))
            .putString(KEY_EVENTS, encodeEvents(events))
            .apply()
        publish()
    }

    /** Returns applied delta or null if already granted today for this source. */
    fun applyTask(source: SpiritualSource): Int? {
        val base = SpiritualEconomy.baseFor(source)
        if (base == 0) return null
        ensureDayBucket()
        refreshMissPenalties()
        val flag = taskFlagKey(source) ?: return null
        if (prefs.getBoolean(flag, false)) return null

        var rate = rateValue()
        val points = SpiritualEconomy.scaledPoints(base, rate)
        var total = SpiritualEconomy.clampTotal(prefs.getInt(KEY_TOTAL, 0) + points)
        var dayScore = prefs.getInt(KEY_DAY_SCORE, 0) + points
        val events = eventsMutable()
        val today = LocalDate.now()
        val lastActive = lastActiveDate()
        var practice = prefs.getInt(KEY_PRACTICE_STREAK, 0)
        val firstPracticeToday = lastActive != today
        if (firstPracticeToday) {
            practice = when {
                lastActive == today.minusDays(1) -> (practice + 1).coerceAtLeast(1)
                else -> 1
            }
            rate = SpiritualEconomy.clampRate(rate + SpiritualEconomy.RATE_PRACTICE_BUMP)
        }
        val reason = when (source) {
            SpiritualSource.ANALYSIS -> SpiritualRu.reasonAnalysis
            SpiritualSource.JOURNAL -> SpiritualRu.reasonJournal
            SpiritualSource.PSYCH -> SpiritualRu.reasonPsych
            SpiritualSource.SUPPORT -> SpiritualRu.reasonSupport
            else -> SpiritualEconomy.sourceLabel(source)
        }
        val id = "task-${source.name.lowercase()}-$today"
        events.add(
            0,
            SpiritualEvent(
                id = id,
                source = source,
                delta = points,
                reason = reason,
                at = System.currentTimeMillis()
            )
        )
        while (events.size > MAX_EVENTS) events.removeAt(events.lastIndex)
        prefs.edit()
            .putBoolean(flag, true)
            .putInt(KEY_TOTAL, total)
            .putInt(KEY_DAY_SCORE, dayScore)
            .putInt(KEY_RATE, toRateInt(rate))
            .putInt(KEY_PRACTICE_STREAK, practice)
            .putInt(KEY_MISS_STREAK, 0)
            .putString(KEY_LAST_ACTIVE, today.toString())
            .putString(KEY_LAST_PENALTY, today.toString())
            .putString(KEY_EVENTS, encodeEvents(events))
            .apply()
        publish()
        return points
    }

    fun applyAiDelta(
        eventId: String,
        score: Int,
        quality: SpiritualQuality,
        reason: String
    ): Boolean {
        ensureDayBucket()
        val ids = appliedAiIds()
        if (eventId in ids) return false
        val clamped = SpiritualEconomy.clampAiScore(score)
        var totalDelta = clamped
        var rate = rateValue()
        rate = SpiritualEconomy.rateAfterQuality(rate, quality)
        if (quality == SpiritualQuality.FICTITIOUS) {
            totalDelta += SpiritualEconomy.FICTITIOUS_PENALTY
        }
        var total = SpiritualEconomy.clampTotal(prefs.getInt(KEY_TOTAL, 0) + totalDelta)
        var dayScore = prefs.getInt(KEY_DAY_SCORE, 0) + totalDelta
        val events = eventsMutable()
        val note = reason.ifBlank {
            if (quality == SpiritualQuality.FICTITIOUS) SpiritualRu.reasonFictitious
            else SpiritualRu.aiDefaultReason
        }
        events.add(
            0,
            SpiritualEvent(
                id = eventId,
                source = SpiritualSource.AI,
                delta = totalDelta,
                reason = note,
                at = System.currentTimeMillis()
            )
        )
        while (events.size > MAX_EVENTS) events.removeAt(events.lastIndex)
        ids.add(eventId)
        while (ids.size > MAX_AI_IDS) ids.removeAt(0)
        prefs.edit()
            .putInt(KEY_TOTAL, total)
            .putInt(KEY_DAY_SCORE, dayScore)
            .putInt(KEY_RATE, toRateInt(rate))
            .putString(KEY_EVENTS, encodeEvents(events))
            .putStringSet(KEY_AI_IDS, ids.toSet())
            .apply()
        publish()
        return true
    }

    /** Parse AI text, apply delta, return text without spiritual block. */
    fun consumeAiText(eventId: String, text: String): String {
        val parsed = SpiritualDeltaParser.parseAndStrip(text) ?: return SpiritualDeltaParser.stripOnly(text)
        applyAiDelta(eventId, parsed.score, parsed.quality, parsed.reason)
        return parsed.visibleText
    }

    private fun ensureDayBucket() {
        val today = LocalDate.now().toString()
        val stored = prefs.getString(KEY_DAY_DATE, null)
        if (stored == today) return
        prefs.edit()
            .putString(KEY_DAY_DATE, today)
            .putInt(KEY_DAY_SCORE, 0)
            .putBoolean(FLAG_ANALYSIS, false)
            .putBoolean(FLAG_JOURNAL, false)
            .putBoolean(FLAG_PSYCH, false)
            .putBoolean(FLAG_SUPPORT, false)
            .apply()
    }

    private fun taskFlagKey(source: SpiritualSource): String? = when (source) {
        SpiritualSource.ANALYSIS -> FLAG_ANALYSIS
        SpiritualSource.JOURNAL -> FLAG_JOURNAL
        SpiritualSource.PSYCH -> FLAG_PSYCH
        SpiritualSource.SUPPORT -> FLAG_SUPPORT
        else -> null
    }

    private fun publish() {
        _snapshot.value = readSnapshot()
    }

    private fun readSnapshot(): SpiritualSnapshot {
        ensureDayBucketQuiet()
        val dayScore = prefs.getInt(KEY_DAY_SCORE, 0)
        return SpiritualSnapshot(
            totalScore = prefs.getInt(KEY_TOTAL, 0),
            dayScore = dayScore,
            rate = rateValue(),
            practiceStreak = prefs.getInt(KEY_PRACTICE_STREAK, 0),
            missStreak = prefs.getInt(KEY_MISS_STREAK, 0),
            dayLabel = SpiritualEconomy.dayLabel(dayScore),
            recent = eventsMutable()
        )
    }

    private fun ensureDayBucketQuiet() {
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_DAY_DATE, null) == today) return
        prefs.edit()
            .putString(KEY_DAY_DATE, today)
            .putInt(KEY_DAY_SCORE, 0)
            .putBoolean(FLAG_ANALYSIS, false)
            .putBoolean(FLAG_JOURNAL, false)
            .putBoolean(FLAG_PSYCH, false)
            .putBoolean(FLAG_SUPPORT, false)
            .apply()
    }

    private fun rateValue(): Float = prefs.getInt(KEY_RATE, 100) / 100f

    private fun toRateInt(rate: Float): Int =
        (SpiritualEconomy.clampRate(rate) * 100f).toInt().coerceIn(50, 200)

    private fun lastActiveDate(): LocalDate? =
        prefs.getString(KEY_LAST_ACTIVE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun lastPenaltyDate(): LocalDate? =
        prefs.getString(KEY_LAST_PENALTY, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun eventsMutable(): MutableList<SpiritualEvent> {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                SpiritualEvent(
                    id = o.optString("id"),
                    source = runCatching {
                        SpiritualSource.valueOf(o.optString("source", SpiritualSource.AI.name))
                    }.getOrDefault(SpiritualSource.AI),
                    delta = o.optInt("delta"),
                    reason = o.optString("reason"),
                    at = o.optLong("at")
                )
            }
        }.getOrElse { mutableListOf() }
    }

    private fun encodeEvents(events: List<SpiritualEvent>): String {
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("source", e.source.name)
                    .put("delta", e.delta)
                    .put("reason", e.reason)
                    .put("at", e.at)
            )
        }
        return arr.toString()
    }

    private fun appliedAiIds(): MutableList<String> =
        prefs.getStringSet(KEY_AI_IDS, emptySet())?.toMutableList() ?: mutableListOf()

    companion object {
        private const val PREFS = "spiritual_rating"
        private const val KEY_TOTAL = "total_score"
        private const val KEY_DAY_DATE = "day_date"
        private const val KEY_DAY_SCORE = "day_score"
        private const val KEY_RATE = "rate_x100"
        private const val KEY_PRACTICE_STREAK = "practice_streak"
        private const val KEY_MISS_STREAK = "miss_streak"
        private const val KEY_LAST_ACTIVE = "last_active"
        private const val KEY_LAST_PENALTY = "last_penalty"
        private const val KEY_EVENTS = "events"
        private const val KEY_AI_IDS = "ai_ids"
        private const val FLAG_ANALYSIS = "did_analysis"
        private const val FLAG_JOURNAL = "did_journal"
        private const val FLAG_PSYCH = "did_psych"
        private const val FLAG_SUPPORT = "did_support"
        private const val MAX_EVENTS = 50
        private const val MAX_AI_IDS = 200
    }
}
