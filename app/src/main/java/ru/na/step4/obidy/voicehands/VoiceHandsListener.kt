package ru.na.step4.obidy.voicehands

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import ru.na.steps12.voice.VoiceI18n

/**
 * Restarting SpeechRecognizer loop. Isolated from the existing one-shot
 * dictation UI so field microphones keep working as before.
 */
internal class VoiceHandsListener(
    context: Context,
    private val onFinal: (String) -> Unit,
    private val onPartial: (String) -> Unit = {},
    private val onState: (listening: Boolean) -> Unit = {}
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wanted = false
    private var paused = false
    private var longSilence = false
    private val restartRunnable = Runnable {
        if (wanted && !paused) ensureStart()
    }

    val available: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(app)

    fun start(longSilence: Boolean) {
        this.longSilence = longSilence
        wanted = true
        paused = false
        main.post { ensureStart() }
    }

    fun pause() {
        paused = true
        main.post {
            cancelRestart()
            runCatching { recognizer?.stopListening() }
            runCatching { recognizer?.cancel() }
            onState(false)
        }
    }

    fun resume() {
        if (!wanted) return
        paused = false
        main.post { scheduleRestart(220) }
    }

    fun stop() {
        wanted = false
        paused = true
        main.post {
            cancelRestart()
            runCatching { recognizer?.stopListening() }
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
            onState(false)
        }
    }

    private fun ensureStart() {
        if (!wanted || paused) return
        if (!available) {
            onState(false)
            return
        }
        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(app).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        runCatching { engine.cancel() }
        runCatching { engine.startListening(intent()) }
            .onSuccess { onState(true) }
            .onFailure {
                onState(false)
                scheduleRestart(500)
            }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!wanted || paused) return
        main.removeCallbacks(restartRunnable)
        main.postDelayed(restartRunnable, delayMs)
    }

    private fun cancelRestart() {
        main.removeCallbacks(restartRunnable)
    }

    private fun intent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, VoiceI18n.speechTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, VoiceI18n.speechTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (longSilence) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 7_000L)
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    7_000L
                )
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4_000L)
            }
        }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onState(true)
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            onState(false)
            val delay = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 80L
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> 450L
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> return
                else -> 280L
            }
            scheduleRestart(delay)
        }

        override fun onResults(results: Bundle?) {
            onState(false)
            val text = firstResult(results)
            if (text.isNotBlank()) onFinal(text)
            scheduleRestart(160)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstResult(partialResults)
            if (text.isNotBlank()) onPartial(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun firstResult(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
}
