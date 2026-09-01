package ru.na.steps12.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(read())
    val config: StateFlow<VoiceConfig> = _config.asStateFlow()

    var snapshot: VoiceConfig
        get() = _config.value
        set(value) {
            prefs.edit()
                .putString(KEY_PUBLIC, value.publicKey)
                .putString(KEY_ASSISTANT, value.assistantId)
                .putString(KEY_PROVIDER, value.provider)
                .putString(KEY_VOICE, value.voiceId)
                .putFloat(KEY_SPEED, value.speed)
                .putBoolean(KEY_OVERRIDE, value.userOverride)
                .apply()
            _config.value = value
        }

    fun applyRemote(
        publicKey: String,
        assistantId: String,
        provider: String,
        voiceId: String,
        speed: Float
    ) {
        val current = snapshot
        val voiceLocked = current.userOverride
        snapshot = current.copy(
            publicKey = publicKey,
            assistantId = assistantId,
            provider = if (voiceLocked) current.provider else provider.ifBlank { current.provider },
            voiceId = if (voiceLocked) current.voiceId else voiceId.ifBlank { current.voiceId },
            speed = if (voiceLocked) current.speed else speed.coerceIn(MIN_SPEED, MAX_SPEED)
        )
    }

    fun setVoice(option: VoiceOption) {
        snapshot = snapshot.copy(
            provider = option.provider,
            voiceId = option.id,
            userOverride = true
        )
    }

    fun setSpeed(speed: Float) {
        snapshot = snapshot.copy(
            speed = speed.coerceIn(MIN_SPEED, MAX_SPEED),
            userOverride = true
        )
    }

    private fun read(): VoiceConfig = VoiceConfig(
        publicKey = prefs.getString(KEY_PUBLIC, "").orEmpty(),
        assistantId = prefs.getString(KEY_ASSISTANT, "").orEmpty(),
        provider = prefs.getString(KEY_PROVIDER, VoiceCatalog.default.provider)
            ?: VoiceCatalog.default.provider,
        voiceId = prefs.getString(KEY_VOICE, VoiceCatalog.default.id)
            ?: VoiceCatalog.default.id,
        speed = prefs.getFloat(KEY_SPEED, 1f).coerceIn(MIN_SPEED, MAX_SPEED),
        userOverride = prefs.getBoolean(KEY_OVERRIDE, false)
    )

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 1.8f
        private const val PREFS = "voice_plugin"
        private const val KEY_PUBLIC = "public_key"
        private const val KEY_ASSISTANT = "assistant_id"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_VOICE = "voice_id"
        private const val KEY_SPEED = "speed"
        private const val KEY_OVERRIDE = "user_override"
    }
}
