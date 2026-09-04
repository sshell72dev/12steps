package ru.na.step4.obidy.data.psych

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import ru.na.step4.obidy.data.streak.DailyStreakStore

/** Daily psychologist streak: one bump per calendar day when a situation is submitted. */
class PsychStreakStore(context: Context) {
    private val inner = DailyStreakStore(context, PREFS)
    val days: StateFlow<Int> get() = inner.days

    fun recordCompletion(): Int = inner.recordCompletion()

    fun refresh() = inner.refresh()

    fun label(count: Int = inner.days.value): String? = inner.label(count)

    fun shouldWarnNow(): Boolean = inner.shouldWarnNow()

    fun markWarnedNow() = inner.markWarnedNow()

    companion object {
        private const val PREFS = "psych_streak"
    }
}
