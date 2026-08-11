package ru.na.step4.obidy.assistant

import ai.vapi.android.Vapi
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ru.na.step4.obidy.BuildConfig
import ru.na.step4.obidy.Ru
import java.time.Instant
import kotlin.coroutines.resume

data class VoiceUiState(
    val configured: Boolean = false,
    val connecting: Boolean = false,
    val inCall: Boolean = false,
    val muted: Boolean = false,
    val status: String = "",
    val lastError: String? = null
)

/**
 * Vapi Android SDK casts Context to Activity for mic permissions —
 * always pass the host Activity, never Application.
 *
 * [Vapi.start] can block waiting for Daily join. Calling it on Main
 * deadlocks UI/WebRTC callbacks — always invoke it off the main thread.
 */
class VapiVoiceController(
    private val scope: CoroutineScope
) {
    private var activity: Activity? = null
    private var lifecycle: Lifecycle? = null
    private var vapi: Vapi? = null
    private var eventsJob: Job? = null
    private var startJob: Job? = null
    private var clientGen = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var watchdog: Runnable? = null

    private val _state = MutableStateFlow(
        VoiceUiState(configured = BuildConfig.VAPI_PUBLIC_KEY.isNotBlank())
    )
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    fun attach(activity: Activity, lifecycle: Lifecycle) {
        if (this.activity === activity && this.lifecycle === lifecycle) return
        if (this.activity !== activity) releaseClient()
        this.activity = activity
        this.lifecycle = lifecycle
    }

    fun isConfigured(): Boolean = BuildConfig.VAPI_PUBLIC_KEY.isNotBlank()

    fun start(
        session: DialogSession,
        extras: Map<String, String>,
        questionFocus: Boolean = false
    ) {
        if (!isConfigured()) {
            _state.update { it.copy(lastError = Ru.voiceNotConfigured, connecting = false) }
            return
        }
        val host = activity
        val life = lifecycle
        if (host == null || life == null) {
            _state.update { it.copy(lastError = Ru.voiceAttachError, connecting = false) }
            return
        }
        if (_state.value.connecting || _state.value.inCall) return

        stopWatchdog()
        startJob?.cancel()
        startJob = scope.launch {
            _state.update { it.copy(connecting = true, lastError = null, status = "connecting") }
            armWatchdog()
            try {
                awaitResumed(life)
                runCatching { vapi?.stop() }
                delay(200)

                val vars = buildVariableValues(session, extras, channel = "voice")
                    .mapValues { (_, v) -> v.truncate(MAX_VAR_CHARS) }
                val firstMessage = if (questionFocus) {
                    AssistantBrief.questionFocusFirstMessage(extras["focus_question"].orEmpty())
                } else if (session.hasPriorDialog) {
                    AssistantBrief.CONTINUE_MESSAGE + " " + LocalFunnel.promptFor(session.funnelStep)
                } else {
                    AssistantBrief.FIRST_MESSAGE
                }
                val prompt = if (questionFocus) {
                    AssistantBrief.resolveQuestionFocusPrompt(vars)
                } else {
                    AssistantBrief.resolvePrompt(vars)
                }

                // Keep overrides small — large payloads stall web-call creation on mobile.
                val overrides = mapOf(
                    "variableValues" to vars,
                    "firstMessage" to firstMessage
                )
                val assistantId = BuildConfig.VAPI_ASSISTANT_ID.trim()
                val client = createClient(host, life)

                Log.i(TAG, "start attempt1 assistantId=${assistantId.isNotBlank()} vars=${vars.size}")
                var result = invokeStart(client) {
                    if (assistantId.isNotBlank()) {
                        start(assistantId = assistantId, assistantOverrides = overrides)
                    } else {
                        start(
                            assistant = compactAssistant(prompt, firstMessage),
                            assistantOverrides = overrides
                        )
                    }
                }

                if (result.isFailure && assistantId.isNotBlank()) {
                    val err1 = result.exceptionOrNull()
                    Log.w(TAG, "assistantId failed: ${detail(err1)}", err1)
                    runCatching { client.stop() }
                    delay(350)
                    val client2 = createClient(host, life)
                    Log.i(TAG, "start attempt2 ephemeral")
                    result = invokeStart(client2) {
                        start(
                            assistant = compactAssistant(prompt, firstMessage),
                            assistantOverrides = overrides
                        )
                    }
                }

                result.fold(
                    onSuccess = {
                        stopWatchdog()
                        markInCall()
                        ensureUnmuted(vapi ?: client)
                        Log.i(TAG, "start success")
                    },
                    onFailure = { err ->
                        Log.e(TAG, "start failure: ${detail(err)}", err)
                        releaseClient()
                        fail(friendlyError(err))
                    }
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "start timeout", e)
                releaseClient()
                fail(Ru.voiceTimeout)
            } catch (e: CancellationException) {
                releaseClient()
                _state.update {
                    it.copy(connecting = false, inCall = false, status = "idle")
                }
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "start crash: ${detail(t)}", t)
                releaseClient()
                fail(friendlyError(t))
            } finally {
                stopWatchdog()
            }
        }
    }

    fun stop() {
        stopWatchdog()
        startJob?.cancel()
        startJob = null
        releaseClient()
        _state.update {
            it.copy(connecting = false, inCall = false, status = "idle", muted = false)
        }
    }

    fun toggleMute() {
        scope.launch {
            runCatching {
                vapi?.toggleMute()
                _state.update { it.copy(muted = !it.muted) }
            }
        }
    }

    fun setError(message: String) {
        stopWatchdog()
        _state.update { it.copy(lastError = message, inCall = false, connecting = false) }
    }

    fun release() {
        stop()
        activity = null
        lifecycle = null
    }

    private suspend fun invokeStart(
        client: Vapi,
        block: suspend Vapi.() -> Result<*>
    ): Result<*> = withTimeout(START_TIMEOUT_MS) {
        // Off Main: Vapi/Daily may block until join while posting callbacks to Main.
        withContext(Dispatchers.IO) {
            Log.i(TAG, "invokeStart begin thread=${Thread.currentThread().name}")
            val r = client.block()
            Log.i(TAG, "invokeStart end success=${r.isSuccess} err=${r.exceptionOrNull()?.message}")
            r
        }
    }

    private suspend fun awaitResumed(life: Lifecycle) {
        if (life.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        Log.i(TAG, "await RESUMED current=${life.currentState}")
        suspendCancellableCoroutine { cont ->
            val observer = object : LifecycleEventObserver {
                override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: Lifecycle.Event) {
                    if (life.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        life.removeObserver(this)
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
            life.addObserver(observer)
            cont.invokeOnCancellation { life.removeObserver(observer) }
            if (life.currentState.isAtLeast(Lifecycle.State.RESUMED) && cont.isActive) {
                life.removeObserver(observer)
                cont.resume(Unit)
            }
        }
    }

    private fun compactAssistant(prompt: String, firstMessage: String): Map<String, Any> = mapOf(
        "name" to AssistantBrief.APP_NAME,
        "firstMessage" to firstMessage,
        "model" to mapOf(
            "provider" to "openai",
            "model" to "gpt-4o-mini",
            "systemPrompt" to prompt.truncate(MAX_PROMPT_CHARS)
        ),
        "voice" to mapOf(
            "provider" to "azure",
            "voiceId" to "ru-RU-SvetlanaNeural"
        ),
        "transcriber" to mapOf(
            "provider" to "deepgram",
            "model" to "nova-2",
            "language" to "ru"
        )
    )

    private fun markInCall() {
        _state.update {
            it.copy(
                connecting = false,
                inCall = true,
                status = "call",
                lastError = null,
                muted = false
            )
        }
    }

    private fun armWatchdog() {
        stopWatchdog()
        val r = Runnable {
            if (_state.value.connecting && !_state.value.inCall) {
                Log.e(TAG, "watchdog")
                startJob?.cancel()
                startJob = null
                releaseClient()
                fail(Ru.voiceTimeout)
            }
        }
        watchdog = r
        mainHandler.postDelayed(r, WATCHDOG_MS)
    }

    private fun stopWatchdog() {
        watchdog?.let { mainHandler.removeCallbacks(it) }
        watchdog = null
    }

    private fun fail(message: String) {
        stopWatchdog()
        _state.update {
            it.copy(connecting = false, inCall = false, status = "idle", lastError = message)
        }
    }

    private fun ensureUnmuted(client: Vapi) {
        scope.launch {
            delay(400)
            runCatching {
                val setMuted = client.javaClass.methods.firstOrNull { m ->
                    m.name == "setMuted" && m.parameterTypes.size == 1
                }
                if (setMuted != null) setMuted.invoke(client, false)
                else if (_state.value.muted) client.toggleMute()
            }
            _state.update { it.copy(muted = false) }
        }
    }

    private fun releaseClient() {
        eventsJob?.cancel()
        eventsJob = null
        val client = vapi
        vapi = null
        clientGen++
        runCatching { client?.stop() }
    }

    private fun createClient(host: Activity, life: Lifecycle): Vapi {
        releaseClient()
        val gen = ++clientGen
        val client = Vapi(
            host,
            life,
            Vapi.Configuration(publicKey = BuildConfig.VAPI_PUBLIC_KEY)
        )
        eventsJob = scope.launch {
            client.eventFlow.collect { event ->
                if (gen != clientGen) return@collect
                Log.i(TAG, "event=${event::class.java.simpleName}")
                when (event) {
                    is Vapi.Event.CallDidStart -> {
                        stopWatchdog()
                        markInCall()
                        ensureUnmuted(client)
                    }
                    is Vapi.Event.CallDidEnd, is Vapi.Event.Hang ->
                        _state.update {
                            it.copy(connecting = false, inCall = false, status = "idle")
                        }
                    is Vapi.Event.Error -> {
                        Log.e(TAG, "event error=${event.error}")
                        _state.update {
                            it.copy(
                                lastError = friendlyError(event.error),
                                connecting = false,
                                inCall = false
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
        vapi = client
        return client
    }

    companion object {
        private const val TAG = "Step4Voice"
        private const val MAX_VAR_CHARS = 1200
        private const val MAX_PROMPT_CHARS = 6000
        private const val START_TIMEOUT_MS = 45_000L
        private const val WATCHDOG_MS = 55_000L

        fun buildVariableValues(
            session: DialogSession,
            extras: Map<String, String>,
            channel: String
        ): Map<String, String> {
            val base = mutableMapOf(
                "current_time_iso" to Instant.now().toString(),
                "channel" to channel,
                "dialog_history" to session.historyText(),
                "funnel_summary" to session.funnelSummary(),
                "funnel_step" to session.funnelStep.key,
                "has_prior_dialog" to session.hasPriorDialog.toString()
            )
            base.putAll(extras)
            return base
        }

        fun detail(error: Any?): String {
            val t = error as? Throwable
            val parts = mutableListOf<String>()
            var cur: Throwable? = t
            var i = 0
            while (cur != null && i < 4) {
                val msg = cur.message?.takeIf { it.isNotBlank() } ?: cur.javaClass.simpleName
                parts += msg
                cur = cur.cause
                i++
            }
            if (parts.isEmpty()) parts += error?.toString().orEmpty()
            return parts.joinToString(" | ").take(280)
        }

        fun friendlyError(error: Any?): String {
            val text = detail(error).lowercase()
            val raw = detail(error)
            return when {
                "responsecanceled" in text ||
                    "createsendtransportfailed" in text ||
                    "mediasoup" in text ->
                    Ru.voiceTransportCanceled
                "permission" in text || "mic" in text || "record_audio" in text ->
                    Ru.micPermissionNeeded
                "timeout" in text || "timed out" in text ->
                    Ru.voiceTimeout
                "unable to resolve" in text ||
                    "unknownhost" in text ||
                    "ssl" in text ||
                    "socket" in text ||
                    "failed to connect" in text ||
                    "network" in text ||
                    "unreachable" in text ||
                    "proxy" in text ->
                    Ru.voiceNetworkError
                "401" in text || "403" in text || "unauthorized" in text || "invalid key" in text ->
                    Ru.voiceAuthError
                "404" in text || "not found" in text || "assistant" in text && "invalid" in text ->
                    Ru.voiceAssistantError
                raw.isBlank() ->
                    Ru.voiceGenericError
                else ->
                    "${Ru.voiceGenericError}\n($raw)"
            }
        }

        private fun String.truncate(max: Int): String =
            if (length <= max) this else take(max) + "…"
    }
}
