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
 * One recognition session at a time. No cancel-before-start and no 80ms
 * restart loop — those two caused the constant mic beep.
 */
internal class VoiceHandsListener(
    context: Context,
    private val onFinal: (String) -> Unit,
    private val onPartial: (String) -> Unit = {},
    private val onState: (listening: Boolean) -> Unit = {}
) {
    private enum class Mode { Idle, Once, Loop }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var mode = Mode.Idle
    private var longSilence = false
    private var sessionOpen = false
    private val restartRunnable = Runnable {
        if (mode == Mode.Loop) beginSession()
    }

    val available: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(app)

    fun listenOnce(longSilence: Boolean) {
        this.longSilence = longSilence
        mode = Mode.Once
        main.post {
            cancelRestart()
            sessionOpen = false
            beginSession()
        }
    }

    fun listenLoop(longSilence: Boolean) {
        this.longSilence = longSilence
        mode = Mode.Loop
        main.post {
            if (sessionOpen) return@post
            beginSession()
        }
    }

    fun stop() {
        mode = Mode.Idle
        main.post {
            cancelRestart()
            sessionOpen = false
            runCatching { recognizer?.destroy() }
            recognizer = null
            onState(false)
        }
    }

    private fun beginSession() {
        if (mode == Mode.Idle) return
        if (sessionOpen) return
        if (!available) {
            onState(false)
            return
        }
        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(app).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val started = runCatching { engine.startListening(intent()) }.isSuccess
        sessionOpen = started
        onState(started)
        if (!started && mode == Mode.Loop) scheduleRestart(2_000)
    }

    private fun scheduleRestart(delayMs: Long) {
        if (mode != Mode.Loop) return
        main.removeCallbacks(restartRunnable)
        main.postDelayed(restartRunnable, delayMs)
    }

    private fun cancelRestart() {
        main.removeCallbacks(restartRunnable)
    }

    private fun finishSession(restartIfLoop: Boolean, delayMs: Long) {
        sessionOpen = false
        onState(false)
        when (mode) {
            Mode.Once, Mode.Idle -> {
                mode = Mode.Idle
                cancelRestart()
            }
            Mode.Loop -> if (restartIfLoop) scheduleRestart(delayMs)
        }
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
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3_000L)
            }
        }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            sessionOpen = true
            onState(true)
        }
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            val delay = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 1_800L
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> 2_400L
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    mode = Mode.Idle
                    finishSession(restartIfLoop = false, delayMs = 0)
                    return
                }
                else -> 2_000L
            }
            finishSession(restartIfLoop = true, delayMs = delay)
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results)
            if (text.isNotBlank()) onFinal(text)
            finishSession(restartIfLoop = true, delayMs = 1_400L)
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
