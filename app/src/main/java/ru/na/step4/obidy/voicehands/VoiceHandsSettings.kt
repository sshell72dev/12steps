package ru.na.step4.obidy.voicehands

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Isolated on/off flag for the experimental hands-free voice loop.
 * Default is off so existing screens keep working until the user opts in.
 */
class VoiceHandsSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
    }

    companion object {
        private const val PREFS = "voicehands_experiment"
        private const val KEY_ENABLED = "enabled"
    }
}
