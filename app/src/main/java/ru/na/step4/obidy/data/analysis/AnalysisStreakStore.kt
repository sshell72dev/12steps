package ru.na.step4.obidy.data.analysis

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import ru.na.step4.obidy.data.streak.DailyStreakStore

/** Daily self-analysis streak: one bump per calendar day when a session is finished. */
class AnalysisStreakStore(context: Context) {
    private val inner = DailyStreakStore(context, PREFS, GRACE_DAYS)
    val days: StateFlow<Int> get() = inner.days

    fun recordCompletion(): Int = inner.recordCompletion()

    fun refresh() = inner.refresh()

    fun label(count: Int = inner.days.value): String? = inner.label(count)

    fun shouldWarnNow(): Boolean = inner.shouldWarnNow()

    fun markWarnedNow() = inner.markWarnedNow()

    companion object {
        private const val PREFS = "analysis_streak"

        /** Серия самоанализа живёт два дня: один пропущенный день её не обнуляет. */
        private const val GRACE_DAYS = 2
    }
}
