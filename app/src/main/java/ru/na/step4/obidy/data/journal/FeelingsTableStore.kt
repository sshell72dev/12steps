package ru.na.step4.obidy.data.journal

import android.content.Context

/**
 * Вкладка таблицы чувств: держим последнюю открытую, чтобы страница
 * при следующем заходе возвращалась на неё же.
 */
class FeelingsTableStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var page: Int
        get() = prefs.getInt(KEY_PAGE, 0).coerceAtLeast(0)
        set(value) {
            prefs.edit().putInt(KEY_PAGE, value.coerceAtLeast(0)).apply()
        }

    companion object {
        private const val PREFS = "journal_feelings_table"
        private const val KEY_PAGE = "page"
    }
}
