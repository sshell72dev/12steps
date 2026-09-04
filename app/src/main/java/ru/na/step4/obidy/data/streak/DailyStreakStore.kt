package ru.na.step4.obidy.data.streak

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.na.step4.obidy.Ru

/**
 * Calendar-day streak. Completing today keeps it through the end of tomorrow;
 * missing that extra day resets the count to zero.
 */
class DailyStreakStore(context: Context, prefsName: String) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val _days = MutableStateFlow(effectiveDays())
    val days: StateFlow<Int> = _days.asStateFlow()

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

    fun shouldWarnNow(now: LocalDateTime = LocalDateTime.now()): Boolean {
        refresh()
        if (_days.value <= 0) return false
        val last = lastDay() ?: return false
        val lastAliveDay = last.plusDays(1)
        if (now.toLocalDate() != lastAliveDay) return false
        if (now.toLocalTime() < WARN_FROM) return false
        return prefs.getString(KEY_WARNED, "") != lastAliveDay.toString()
    }

    fun markWarnedNow(now: LocalDateTime = LocalDateTime.now()) {
        prefs.edit().putString(KEY_WARNED, now.toLocalDate().toString()).apply()
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
        private const val KEY_DAY = "last_day"
        private const val KEY_COUNT = "count"
        private const val KEY_WARNED = "warned_day"
        private val WARN_FROM = LocalTime.of(23, 0)
    }
}

internal fun Int.ruDayWord(): String {
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
