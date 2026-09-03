package ru.na.step4.obidy.voicehands

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ru.na.step4.obidy.ui.psych.PsychUi
import ru.na.steps12.voice.VoiceSpeaker

/**
 * Experimental hands-free loop. Owns listening, spoken replies and when
 * to call the psychologist façade. Existing psych buttons keep working.
 */
class VoiceHandsController(
    context: Context,
    private val settings: VoiceHandsSettings,
    private val speaker: VoiceSpeaker,
    private val openPsych: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val listener = VoiceHandsListener(
        context = context,
        onFinal = { text -> scope.launch { mutex.withLock { onHeard(text) } } },
        onPartial = { text -> publish { copy(lastHeard = text) } },
        onState = { listening ->
            publish {
                copy(
                    listening = listening,
                    showListenButton = VoiceHandsRu.canTapListen(phase) && !listening && !speaking
                )
            }
        }
    )

    private val _ui = MutableStateFlow(VoiceHandsUi())
    val ui: StateFlow<VoiceHandsUi> = _ui.asStateFlow()

    private var draft = ""
    private var pendingRead = ""
    private var spokenQuestion: String? = null
    private var handledResultKey: String? = null
    private var skippedTopicPick = false
    private var psychJob: Job? = null
    private var thinkJob: Job? = null
    private var foreground = true

    init {
        scope.launch {
            settings.enabled.collectLatest { on ->
                mutex.withLock {
                    if (on) enterEnabled() else shutdown()
                }
            }
        }
        psychJob = scope.launch {
            VoiceHandsPsychGate.bound.collectLatest { psych ->
                if (psych == null) return@collectLatest
                psych.ui.collect { ui -> onPsychUi(psych, ui) }
            }
        }
    }

    fun onForeground() {
        foreground = true
        if (settings.enabled.value) listenForPhase(_ui.value.phase)
    }

    fun onBackground() {
        foreground = false
        listener.stop()
        publish {
            copy(
                listening = false,
                showListenButton = VoiceHandsRu.canTapListen(phase)
            )
        }
    }

    fun returnToStandby() {
        speaker.stop()
        listener.stop()
        thinkJob?.cancel()
        scope.launch {
            mutex.withLock {
                if (!settings.enabled.value) return@withLock
                VoiceHandsPsychGate.bound.value?.goHub()
                enterStandby()
            }
        }
    }

    fun listenNow() {
        scope.launch {
            mutex.withLock {
                if (!settings.enabled.value || !foreground) return@withLock
                val phase = _ui.value.phase
                if (!VoiceHandsRu.canTapListen(phase)) return@withLock
                startListen(phase)
            }
        }
    }

    fun disable() {
        speaker.stop()
        listener.stop()
        thinkJob?.cancel()
        settings.setEnabled(false)
    }

    fun release() {
        thinkJob?.cancel()
        psychJob?.cancel()
        listener.stop()
        speaker.stop()
        scope.cancel()
    }

    private suspend fun enterEnabled() {
        if (!listener.available) {
            publish {
                VoiceHandsUi(
                    enabled = true,
                    phase = VoiceHandsPhase.Off,
                    status = VoiceHandsRu.off,
                    error = VoiceHandsRu.noEngine
                )
            }
            return
        }
        enterStandby()
    }

    private fun shutdown() {
        listener.stop()
        if (_ui.value.speaking) speaker.stop()
        draft = ""
        pendingRead = ""
        spokenQuestion = null
        handledResultKey = null
        skippedTopicPick = false
        thinkJob?.cancel()
        publish {
            VoiceHandsUi(enabled = false, phase = VoiceHandsPhase.Off, status = VoiceHandsRu.off)
        }
    }

    private suspend fun enterStandby() {
        draft = ""
        pendingRead = ""
        spokenQuestion = null
        handledResultKey = null
        skippedTopicPick = false
        thinkJob?.cancel()
        if (_ui.value.speaking) speaker.stop()
        listener.stop()
        setPhase(VoiceHandsPhase.Standby)
    }

    private suspend fun onHeard(text: String) {
        val spoken = text.trim()
        if (spoken.isBlank() || !settings.enabled.value) return
        publish { copy(lastHeard = spoken, error = null) }
        when (_ui.value.phase) {
            VoiceHandsPhase.Standby -> if (VoiceHandsPhrases.matchCommand(spoken) == VoiceHandsCommand.Start) {
                beginRecord()
            }
            VoiceHandsPhase.Dictating -> {
                if (VoiceHandsPhrases.matchCommand(spoken) == VoiceHandsCommand.Standby) {
                    VoiceHandsPsychGate.bound.value?.goHub()
                    enterStandby()
                } else {
                    handleDictation(spoken)
                }
            }
            VoiceHandsPhase.AwaitingReply -> handleReply(spoken)
            VoiceHandsPhase.AskRead -> handleAskRead(spoken)
            VoiceHandsPhase.AfterRead -> handleAfterRead(spoken)
            else -> Unit
        }
    }

    private suspend fun beginRecord() {
        setPhase(VoiceHandsPhase.Opening)
        listener.stop()
        openPsych()
        val psych = withTimeoutOrNull(6_000) {
            VoiceHandsPsychGate.bound.first { it != null }
        }
        if (psych == null) {
            publish { copy(error = VoiceHandsRu.psychMissing) }
            enterStandby()
            return
        }
        delay(250)
        val page = psych.ui.value
        when {
            page.isOnboarding -> psych.skipOnboarding()
            !page.isRecord -> psych.goRecord()
        }
        draft = ""
        spokenQuestion = null
        handledResultKey = null
        skippedTopicPick = false
        say(VoiceHandsRu.SAY_DICTATE)
        setPhase(VoiceHandsPhase.Dictating)
        listenForPhase(VoiceHandsPhase.Dictating)
    }

    private suspend fun handleDictation(spoken: String) {
        val (body, done) = VoiceHandsPhrases.stripTrailingDone(spoken)
        if (body.isNotBlank()) {
            draft = mergeDraft(draft, body)
            publish { copy(draft = draft) }
        }
        if (!done) return
        if (draft.isBlank()) {
            say(VoiceHandsRu.emptyDictation)
            return
        }
        val psych = VoiceHandsPsychGate.bound.value
        if (psych == null) {
            openPsych()
            delay(400)
        }
        val bound = VoiceHandsPsychGate.bound.value
        if (bound == null) {
            publish { copy(error = VoiceHandsRu.psychMissing) }
            return
        }
        say(VoiceHandsRu.SAY_THINKING)
        beginThinking(VoiceHandsPhase.ThinkingQuestion)
        bound.submitSituation(draft)
    }

    private suspend fun handleReply(spoken: String) {
        when (VoiceHandsPhrases.matchCommand(spoken)) {
            VoiceHandsCommand.Analyze -> runAiCommand { it.analyze() }
            VoiceHandsCommand.Recommend -> runAiCommand { it.recommend() }
            VoiceHandsCommand.Standby -> {
                VoiceHandsPsychGate.bound.value?.goHub()
                enterStandby()
            }
            VoiceHandsCommand.Start,
            VoiceHandsCommand.Done,
            VoiceHandsCommand.Read,
            null -> {
                val psych = VoiceHandsPsychGate.bound.value ?: return
                spokenQuestion = null
                say(VoiceHandsRu.SAY_THINKING)
                beginThinking(VoiceHandsPhase.ThinkingQuestion)
                psych.answerDialogue(spoken)
            }
        }
    }

    private suspend fun handleAskRead(spoken: String) {
        when (VoiceHandsPhrases.matchCommand(spoken)) {
            VoiceHandsCommand.Read -> readPending()
            VoiceHandsCommand.Analyze -> runAiCommand { it.analyze() }
            VoiceHandsCommand.Recommend -> runAiCommand { it.recommend() }
            VoiceHandsCommand.Standby -> {
                VoiceHandsPsychGate.bound.value?.goHub()
                enterStandby()
            }
            else -> Unit
        }
    }

    private suspend fun handleAfterRead(spoken: String) {
        when (VoiceHandsPhrases.matchCommand(spoken)) {
            VoiceHandsCommand.Standby -> {
                VoiceHandsPsychGate.bound.value?.goHub()
                enterStandby()
            }
            VoiceHandsCommand.Analyze -> runAiCommand { it.analyze() }
            VoiceHandsCommand.Recommend -> runAiCommand { it.recommend() }
            VoiceHandsCommand.Start -> beginRecord()
            else -> Unit
        }
    }

    private suspend fun runAiCommand(block: (VoiceHandsPsych) -> Unit) {
        val psych = VoiceHandsPsychGate.bound.value ?: return
        handledResultKey = null
        say(VoiceHandsRu.SAY_THINKING)
        beginThinking(VoiceHandsPhase.ThinkingResult)
        block(psych)
    }

    private suspend fun readPending() {
        val text = pendingRead
        if (text.isBlank()) {
            setPhase(VoiceHandsPhase.AfterRead)
            listenForPhase(VoiceHandsPhase.AfterRead)
            return
        }
        setPhase(VoiceHandsPhase.Reading)
        say(text)
        say(VoiceHandsRu.SAY_READ_DONE)
        setPhase(VoiceHandsPhase.AfterRead)
        listenForPhase(VoiceHandsPhase.AfterRead)
    }

    private suspend fun onPsychUi(psych: VoiceHandsPsych, ui: PsychUi) {
        val follow = mutex.withLock { inspectPsych(psych, ui) } ?: return
        thinkJob?.cancel()
        say(follow.text)
        mutex.withLock {
            when (follow.kind) {
                FollowKind.Question -> if (_ui.value.phase == VoiceHandsPhase.ThinkingQuestion) {
                    setPhase(VoiceHandsPhase.AwaitingReply)
                    listenForPhase(VoiceHandsPhase.AwaitingReply)
                }
                FollowKind.ReadyRead -> if (_ui.value.phase == VoiceHandsPhase.ThinkingResult) {
                    pendingRead = follow.speakable
                    setPhase(VoiceHandsPhase.AskRead)
                    listenForPhase(VoiceHandsPhase.AskRead)
                }
                FollowKind.Error -> {
                    setPhase(VoiceHandsPhase.AfterRead)
                    listenForPhase(VoiceHandsPhase.AfterRead)
                    publish { copy(error = follow.text) }
                }
            }
        }
    }

    private fun inspectPsych(psych: VoiceHandsPsych, ui: PsychUi): FollowUp? {
        return when (_ui.value.phase) {
            VoiceHandsPhase.ThinkingQuestion -> {
                if (ui.isPaywall) {
                    return FollowUp(FollowKind.Error, VoiceHandsRu.psychMissing)
                }
                if (!ui.error.isNullOrBlank() && !ui.waiting) {
                    return FollowUp(FollowKind.Error, ui.error)
                }
                val topicId = ui.topicPickId
                if (topicId != null && !skippedTopicPick) {
                    skippedTopicPick = true
                    psych.pickTopicNoHistory(topicId)
                    return null
                }
                val question = ui.dialogueQuestion
                if (question != null && spokenQuestion != question) {
                    spokenQuestion = question
                    FollowUp(FollowKind.Question, question)
                } else {
                    null
                }
            }
            VoiceHandsPhase.ThinkingResult -> {
                if (ui.isPaywall) {
                    return FollowUp(FollowKind.Error, VoiceHandsRu.psychMissing)
                }
                if (!ui.error.isNullOrBlank() && !ui.waiting) {
                    return FollowUp(FollowKind.Error, ui.error)
                }
                val key = ui.resultKey
                val speakable = ui.resultSpeakable
                if (key != null && speakable != null && handledResultKey != key) {
                    handledResultKey = key
                    FollowUp(FollowKind.ReadyRead, VoiceHandsRu.SAY_READY_READ, speakable)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun beginThinking(phase: VoiceHandsPhase) {
        setPhase(phase)
        armThinkWatch()
    }

    private fun armThinkWatch() {
        thinkJob?.cancel()
        thinkJob = scope.launch {
            delay(THINK_TIMEOUT_MS)
            val stuck = mutex.withLock {
                val phase = _ui.value.phase
                phase == VoiceHandsPhase.ThinkingQuestion || phase == VoiceHandsPhase.ThinkingResult
            }
            if (!stuck) return@launch
            speaker.stop()
            say(VoiceHandsRu.SAY_TIMEOUT)
            mutex.withLock {
                val phase = _ui.value.phase
                if (phase == VoiceHandsPhase.ThinkingQuestion || phase == VoiceHandsPhase.ThinkingResult) {
                    setPhase(VoiceHandsPhase.AfterRead)
                    listenForPhase(VoiceHandsPhase.AfterRead)
                    publish { copy(error = VoiceHandsRu.thinkTimeout) }
                }
            }
        }
    }

    private fun listenForPhase(phase: VoiceHandsPhase) {
        if (!settings.enabled.value || !foreground) {
            listener.stop()
            return
        }
        when (phase) {
            VoiceHandsPhase.Dictating,
            VoiceHandsPhase.AwaitingReply,
            VoiceHandsPhase.AskRead,
            VoiceHandsPhase.AfterRead -> startListen(phase)
            else -> listener.stop()
        }
    }

    private fun startListen(phase: VoiceHandsPhase) {
        val long = phase == VoiceHandsPhase.Dictating || phase == VoiceHandsPhase.AwaitingReply
        when (phase) {
            VoiceHandsPhase.Standby,
            VoiceHandsPhase.AskRead,
            VoiceHandsPhase.AfterRead -> listener.listenOnce(longSilence = false)
            VoiceHandsPhase.Dictating,
            VoiceHandsPhase.AwaitingReply -> listener.listenLoop(longSilence = long)
            else -> listener.stop()
        }
    }

    private suspend fun say(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        listener.stop()
        speaker.stop()
        publish { copy(speaking = true, listening = false, showListenButton = false) }
        speaker.speak(clean)
        delay(100)
        val began = withTimeoutOrNull(1_000) { speaker.speaking.first { it } }
        if (began == true) {
            withTimeoutOrNull(45_000) { speaker.speaking.first { !it } }
        }
        if (speaker.speaking.value) speaker.stop()
        delay(200)
        publish {
            copy(
                speaking = false,
                showListenButton = VoiceHandsRu.canTapListen(phase) && !listening
            )
        }
    }

    private fun setPhase(phase: VoiceHandsPhase) {
        publish {
            copy(
                enabled = true,
                phase = phase,
                status = statusFor(phase),
                draft = draft,
                commandsHint = VoiceHandsRu.hintsFor(phase),
                showListenButton = VoiceHandsRu.canTapListen(phase) && !listening && !speaking,
                error = if (phase == VoiceHandsPhase.Standby) null else error
            )
        }
    }

    private fun statusFor(phase: VoiceHandsPhase): String = when (phase) {
        VoiceHandsPhase.Off -> VoiceHandsRu.off
        VoiceHandsPhase.Standby -> VoiceHandsRu.standby
        VoiceHandsPhase.Opening -> VoiceHandsRu.opening
        VoiceHandsPhase.Dictating -> VoiceHandsRu.dictating
        VoiceHandsPhase.ThinkingQuestion,
        VoiceHandsPhase.ThinkingResult -> VoiceHandsRu.thinking
        VoiceHandsPhase.AwaitingReply -> VoiceHandsRu.awaiting
        VoiceHandsPhase.AskRead -> VoiceHandsRu.askRead
        VoiceHandsPhase.Reading -> VoiceHandsRu.reading
        VoiceHandsPhase.AfterRead -> VoiceHandsRu.afterRead
    }

    private fun publish(block: VoiceHandsUi.() -> VoiceHandsUi) {
        _ui.value = _ui.value.block()
    }

    private fun mergeDraft(current: String, incoming: String): String {
        val piece = incoming.trim()
        if (piece.isBlank()) return current
        if (current.isBlank()) return piece
        if (current.endsWith(piece, ignoreCase = true)) return current
        return "$current $piece".replace(Regex("\\s+"), " ").trim()
    }

    private enum class FollowKind { Question, ReadyRead, Error }

    private data class FollowUp(
        val kind: FollowKind,
        val text: String,
        val speakable: String = ""
    )

    companion object {
        private const val THINK_TIMEOUT_MS = 60_000L
    }
}
