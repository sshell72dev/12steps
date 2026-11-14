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

    /**
     * Место, на котором остановилось прослушивание. Лежит в prefs, потому что
     * система может выгрузить процесс, пока панель стоит на паузе: движок TTS
     * тогда создаётся заново, и без этой записи текст пришлось бы начинать сначала.
     */
    fun readListenPoint(): ListenPoint? {
        val text = prefs.getString(KEY_LISTEN_TEXT, "").orEmpty()
        val savedAt = prefs.getLong(KEY_LISTEN_AT, 0L)
        if (text.isBlank() || savedAt <= 0L) return null
        if (System.currentTimeMillis() - savedAt > LISTEN_TTL_MS) {
            clearListenPoint()
            return null
        }
        return ListenPoint(
            text = text,
            chunkIndex = prefs.getInt(KEY_LISTEN_CHUNK, 0),
            positionMs = prefs.getLong(KEY_LISTEN_POS, 0L)
        )
    }

    fun saveListenPoint(text: String, chunkIndex: Int, positionMs: Long) {
        if (text.isBlank()) return
        prefs.edit()
            .putString(KEY_LISTEN_TEXT, text)
            .putInt(KEY_LISTEN_CHUNK, chunkIndex)
            .putLong(KEY_LISTEN_POS, positionMs)
            .putLong(KEY_LISTEN_AT, System.currentTimeMillis())
            .apply()
    }

    fun clearListenPoint() {
        prefs.edit()
            .remove(KEY_LISTEN_TEXT)
            .remove(KEY_LISTEN_CHUNK)
            .remove(KEY_LISTEN_POS)
            .remove(KEY_LISTEN_AT)
            .apply()
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
        private const val KEY_LISTEN_TEXT = "listen_text"
        private const val KEY_LISTEN_CHUNK = "listen_chunk"
        private const val KEY_LISTEN_POS = "listen_position"
        private const val KEY_LISTEN_AT = "listen_saved_at"

        /** Дольше суток точка прослушивания неактуальна — не воскрешаем панель. */
        private const val LISTEN_TTL_MS = 24 * 60 * 60 * 1000L
    }
}

/** Сохранённое место прослушивания: текст, порция и миллисекунды от начала. */
data class ListenPoint(
    val text: String,
    val chunkIndex: Int,
    val positionMs: Long
)
