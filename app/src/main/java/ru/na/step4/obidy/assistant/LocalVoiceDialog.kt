package ru.na.step4.obidy.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.na.step4.obidy.Ru
import ru.na.steps12.voice.VoiceI18n
import ru.na.steps12.voice.VoiceSpeaker

/**
 * Голосовой диалог консультанта без внешних сервисов: микрофон слушает
 * системный распознаватель речи, ответы читает голос телефона.
 *
 * [onHeard] получает распознанный текст и возвращает реплику консультанта,
 * которую нужно озвучить.
 */
class LocalVoiceDialog(
    context: Context,
    private val scope: CoroutineScope,
    private val speaker: VoiceSpeaker?,
    private val onHeard: (String) -> String
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var muted = false

    private val _state = MutableStateFlow(VoiceUiState(configured = speechAvailable()))
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    fun start() {
        if (active) return
        if (!speechAvailable()) {
            _state.update {
                it.copy(
                    configured = false,
                    inCall = false,
                    connecting = false,
                    lastError = Ru.voiceNoSpeech
                )
            }
            return
        }
        active = true
        muted = false
        _state.update {
            it.copy(
                configured = true,
                inCall = true,
                connecting = false,
                muted = false,
                status = "listening",
                lastError = null
            )
        }
        listen()
    }

    fun stop() {
        active = false
        muted = false
        main.removeCallbacksAndMessages(null)
        stopListening()
        speaker?.stop()
        _state.update {
            it.copy(
                inCall = false,
                connecting = false,
                muted = false,
                status = "idle",
                lastError = null
            )
        }
    }

    fun toggleMute() {
        if (!active) return
        muted = !muted
        if (muted) {
            stopListening()
            speaker?.stop()
        } else {
            listen()
        }
        _state.update { it.copy(muted = muted, status = if (muted) "muted" else "listening") }
    }

    fun setError(message: String) {
        _state.update { it.copy(lastError = message, inCall = false, connecting = false) }
    }

    fun release() {
        active = false
        muted = false
        main.removeCallbacksAndMessages(null)
        stopListening()
        speaker?.stop()
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun speechAvailable(): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(app) }.getOrDefault(false)

    private fun listen(delayMs: Long = 0L) {
        if (!active || muted) return
        main.postDelayed({
            if (!active || muted) return@postDelayed
            val engine = recognizer ?: runCatching {
                SpeechRecognizer.createSpeechRecognizer(app).also {
                    it.setRecognitionListener(listener)
                    recognizer = it
                }
            }.getOrNull()
            if (engine == null) {
                fail(Ru.voiceListenFailed)
                return@postDelayed
            }
            val started = runCatching { engine.startListening(intent()) }.isSuccess
            if (!started) fail(Ru.voiceListenFailed)
        }, delayMs)
    }

    private fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    private fun fail(message: String) {
        active = false
        muted = false
        main.removeCallbacksAndMessages(null)
        stopListening()
        speaker?.stop()
        _state.update {
            it.copy(
                inCall = false,
                connecting = false,
                muted = false,
                status = "idle",
                lastError = message
            )
        }
    }

    private fun heard(text: String) {
        val reply = runCatching { onHeard(text) }.getOrDefault("")
        if (!active || muted) return
        if (reply.isBlank()) {
            listen(RESTART_DELAY_MS)
            return
        }
        speakAndListen(reply)
    }

    private fun speakAndListen(reply: String) {
        val tts = speaker
        if (tts == null) {
            listen(RESTART_DELAY_MS)
            return
        }
        _state.update { it.copy(status = "speaking") }
        scope.launch {
            // Без готового движка ответ уйдёт в очередь, а микрофон уже начнёт слушать.
            withTimeoutOrNull(TTS_READY_TIMEOUT_MS) { tts.ready.first { it } }
            tts.speak(reply)
            delay(SPEAK_GRACE_MS)
            val began = withTimeoutOrNull(2_000) { tts.speaking.first { it } }
            if (began == true) {
                withTimeoutOrNull(SPEAK_TIMEOUT_MS) { tts.speaking.first { !it } }
            }
            if (tts.speaking.value) tts.stop()
            if (!active || muted) return@launch
            _state.update { it.copy(status = "listening") }
            listen(RESTART_DELAY_MS)
        }
    }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, VoiceI18n.speechTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, VoiceI18n.speechTag)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
            SILENCE_MS
        )
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onError(error: Int) {
            if (!active || muted) return
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail(Ru.micPermissionNeeded)
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> listen(RESTART_DELAY_MS)
                // Занятый распознаватель — ждём дольше: параллельно могла идти диктовка.
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> listen(RETRY_DELAY_MS)
                else -> listen(RETRY_DELAY_MS)
            }
        }

        override fun onResults(results: Bundle?) {
            if (!active || muted) return
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isBlank()) {
                listen(RESTART_DELAY_MS)
                return
            }
            heard(text)
        }
    }

    companion object {
        private const val TTS_READY_TIMEOUT_MS = 3_000L
        private const val RESTART_DELAY_MS = 350L
        private const val RETRY_DELAY_MS = 900L
        private const val SPEAK_GRACE_MS = 250L
        private const val SPEAK_TIMEOUT_MS = 120_000L
        private const val SILENCE_MS = 2_500L
    }
}
