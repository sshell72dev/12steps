package ru.na.step4.obidy.data.analysis

import android.content.Context
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.na.step4.obidy.Ru

/** Daily self-analysis streak: one bump per calendar day when a session is finished. */
class AnalysisStreakStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _days = MutableStateFlow(effectiveDays())
    val days: StateFlow<Int> = _days.asStateFlow()

    /** Call once when the user answers the last question of a session. */
    fun recordCompletion(): Int {
        val today = LocalDate.now()
        val last = lastDay()
        val count = prefs.getInt(KEY_COUNT, 0)
        val next = when {
            last == today -> count.coerceAtLeast(1)
            last == today.minusDays(1) -> (count + 1).coerceAtLeast(1)
            else -> 1
        }
        prefs.edit()
            .putString(KEY_DAY, today.toString())
            .putInt(KEY_COUNT, next)
            .apply()
        _days.value = next
        return next
    }

    fun refresh() {
        _days.value = effectiveDays()
    }

    fun label(days: Int = _days.value): String? {
        if (days <= 0) return null
        return "${Ru.analysisStreak} · ${days.ruDayWord()}"
    }

    private fun effectiveDays(): Int {
        val last = lastDay() ?: return 0
        val count = prefs.getInt(KEY_COUNT, 0)
        if (count <= 0) return 0
        val today = LocalDate.now()
        return if (last == today || last == today.minusDays(1)) count else 0
    }

    private fun lastDay(): LocalDate? =
        prefs.getString(KEY_DAY, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    companion object {
        private const val PREFS = "analysis_streak"
        private const val KEY_DAY = "last_day"
        private const val KEY_COUNT = "count"
    }
}

private fun Int.ruDayWord(): String {
    val n = this % 100
    val n1 = this % 10
    val word = when {
        n in 11..14 -> Ru.lockDay5
        n1 == 1 -> Ru.lockDay1
        n1 in 2..4 -> Ru.lockDay2
        else -> Ru.lockDay5
    }
    return "$this $word"
}
