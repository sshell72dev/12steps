package ru.na.step4.obidy.data.update

import android.content.Context

/** Локальная память: какую версию отложили и когда последний раз проверяли. */
class UpdateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var postponedVersionCode: Int
        get() = prefs.getInt(KEY_POSTPONED, 0)
        set(value) {
            prefs.edit().putInt(KEY_POSTPONED, value).apply()
        }

    var lastCheckMs: Long
        get() = prefs.getLong(KEY_CHECKED, 0L)
        set(value) {
            prefs.edit().putLong(KEY_CHECKED, value).apply()
        }

    fun isPostponed(versionCode: Int): Boolean =
        versionCode > 0 && postponedVersionCode == versionCode

    companion object {
        private const val PREFS = "app_update"
        private const val KEY_POSTPONED = "postponed_version_code"
        private const val KEY_CHECKED = "last_check_ms"
    }
}
