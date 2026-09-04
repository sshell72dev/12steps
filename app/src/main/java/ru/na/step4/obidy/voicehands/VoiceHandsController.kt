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
            onPartial = { text ->
            publish { copy(lastHeard = text) }
            if (listing) maybeBargeIn(text)
            if (listingTopics) maybeTopicBargeIn(text)
        },
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
    /** When waiting for analyze/recommend, ignore a stale Result of another kind. */
    private var expectedResultKind: String? = null
    private var skippedTopicPick = false
    private var topicSituationId: Long? = null
    private var pendingTopicChoice: VoiceTopicChoice? = null
    private var topicCatalogCache: List<ru.na.step4.obidy.data.psych.PsychTopic> = emptyList()
    private var psychJob: Job? = null
    private var thinkJob: Job? = null
    private var listJob: Job? = null
    private var foreground = true
    @Volatile private var listing = false
    @Volatile private var listingTopics = false
    @Volatile private var listingEcho = ""
    @Volatile private var bargeCommand: VoiceHandsCommand? = null
    @Volatile private var bargeTopicChoice: VoiceTopicChoice? = null
    @Volatile private var offerArmed = false
    @Volatile private var suppressReviewOffer = false

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
        listJob?.cancel()
        listing = false
        listingTopics = false
        bargeCommand = null
        bargeTopicChoice = null
        offerArmed = false
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
        listJob?.cancel()
        listing = false
        bargeCommand = null
        offerArmed = false
        settings.setEnabled(false)
    }

    fun release() {
        thinkJob?.cancel()
        listJob?.cancel()
        listing = false
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
        expectedResultKind = null
        skippedTopicPick = false
        topicSituationId = null
        pendingTopicChoice = null
        topicCatalogCache = emptyList()
        thinkJob?.cancel()
        listJob?.cancel()
        listing = false
        listingTopics = false
        bargeCommand = null
        bargeTopicChoice = null
        offerArmed = false
        publish {
            VoiceHandsUi(enabled = false, phase = VoiceHandsPhase.Off, status = VoiceHandsRu.off)
        }
    }

    private suspend fun enterStandby() {
        draft = ""
        pendingRead = ""
        spokenQuestion = null
        handledResultKey = null
        expectedResultKind = null
        skippedTopicPick = false
        topicSituationId = null
        pendingTopicChoice = null
        topicCatalogCache = emptyList()
        thinkJob?.cancel()
        listJob?.cancel()
        listing = false
        listingTopics = false
        bargeCommand = null
        bargeTopicChoice = null
        offerArmed = false
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
            VoiceHandsPhase.SuggestTopic -> handleSuggestTopic(spoken)
            VoiceHandsPhase.ListTopics -> handleListTopics(spoken)
            VoiceHandsPhase.ConfirmTopic -> handleConfirmTopic(spoken)
            VoiceHandsPhase.NameTopic -> handleNameTopic(spoken)
            VoiceHandsPhase.AskRead -> handleAskRead(spoken)
            VoiceHandsPhase.AfterRead -> handleAfterRead(spoken)
            VoiceHandsPhase.OfferActions -> handleOffer(spoken)
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
        expectedResultKind = null
        skippedTopicPick = false
        topicSituationId = null
        pendingTopicChoice = null
        topicCatalogCache = emptyList()
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
            VoiceHandsCommand.Analyze -> runAiCommand("analyze") { it.analyze() }
            VoiceHandsCommand.Recommend -> runAiCommand("recommend") { it.recommend() }
            VoiceHandsCommand.Work -> runAiCommand(null) { it.startWork() }
            VoiceHandsCommand.Standby -> {
                VoiceHandsPsychGate.bound.value?.goHub()
                enterStandby()
            }
            VoiceHandsCommand.Start,
            VoiceHandsCommand.Done,
            VoiceHandsCommand.Read,
            VoiceHandsCommand.Confirm,
            VoiceHandsCommand.ListTopics,
            VoiceHandsCommand.NameTopic,
            VoiceHandsCommand.NoTopic,
            null -> {
                val psych = VoiceHandsPsychGate.bound.value ?: return
                if (psych.willCompleteDialogueOnNextAnswer()) {
                    spokenQuestion = null
                    psych.answerDialogue(spoken)
                    startOfferActions()
                    return
                }
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
            VoiceHandsCommand.Analyze -> runAiCommand("analyze") { it.analyze() }
            VoiceHandsCommand.Recommend -> runAiCommand("recommend") { it.recommend() }
            VoiceHandsCommand.Work -> runAiCommand(null) { it.startWork() }
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
            VoiceHandsCommand.Analyze -> runAiCommand("analyze") { it.analyze() }
            VoiceHandsCommand.Recommend -> runAiCommand("recommend") { it.recommend() }
            VoiceHandsCommand.Work -> runAiCommand(null) { it.startWork() }
            VoiceHandsCommand.Start -> beginRecord()
            else -> Unit
        }
    }

    private suspend fun beginSuggestTopic(situationId: Long) {
        val psych = VoiceHandsPsychGate.bound.value ?: return
        topicSituationId = situationId
        thinkJob?.cancel()
        topicCatalogCache = psych.topicCatalog()
        val suggestion = psych.suggestTopic(situationId)
        pendingTopicChoice = suggestion.takeUnless { it.isEmpty }
        val speech = when {
            suggestion.topicId != null ->
                "По ситуации предлагаю тему «${suggestion.displayName}». ${VoiceHandsRu.SAY_TOPIC_HINT}"
            !suggestion.createName.isNullOrBlank() ->
                "Подходящей темы среди имеющихся нет. Предлагаю создать тему «${suggestion.displayName}». ${VoiceHandsRu.SAY_TOPIC_HINT}"
            else ->
                "Выберите тему. ${VoiceHandsRu.SAY_TOPIC_HINT}"
        }
        say(speech)
        setPhase(VoiceHandsPhase.SuggestTopic)
        listenForPhase(VoiceHandsPhase.SuggestTopic)
    }

    private suspend fun handleSuggestTopic(spoken: String) {
        when (VoiceHandsPhrases.matchCommand(spoken)) {
            VoiceHandsCommand.Confirm -> applyPendingTopic()
            VoiceHandsCommand.ListTopics -> startTopicListing()
            VoiceHandsCommand.NameTopic -> askNameTopic()
            VoiceHandsCommand.NoTopic -> applyTopicChoice(VoiceTopicChoice(displayName = "без темы"))
            VoiceHandsCommand.Standby -> {
                VoiceHandsPsychGate.bound.value?.goHub()
                enterStandby()
            }
            else -> {
                val matched = matchSpokenTopic(spoken)
                if (matched != null) askConfirmTopic(matched) else Unit
            }
        }
    }

    private suspend fun handleListTopics(spoken: String) {
        if (listingTopics) {
            maybeTopicBargeIn(spoken)
            return
        }
        when (VoiceHandsPhrases.matchCommand(spoken)) {
            VoiceHandsCommand.ListTopics -> startTopicListing()
            VoiceHandsCommand.NameTopic -> askNameTopic()
            VoiceHandsCommand.NoTopic -> applyTopicChoice(VoiceTopicChoice(displayName = "без темы"))
            VoiceHandsCommand.Standby -> {
                VoiceHandsPsychGate.bound.value?.goHub()
                enterStandby()
            }
            else -> {
                val matched = matchSpokenTopic(spoken)
                if (matched != null) askConfirmTopic(matched) else Unit
            }
        }
    }

    private suspend fun handleConfirmTopic(spoken: String) {
        when (VoiceHandsPhrases.matchCommand(spoken)) {
            VoiceHandsCommand.Confirm -> applyPendingTopic()
            VoiceHandsCommand.ListTopics -> startTopicListing()
            VoiceHandsCommand.NameTopic -> askNameTopic()
            VoiceHandsCommand.NoTopic -> applyTopicChoice(VoiceTopicChoice(displayName = "без темы"))
            VoiceHandsCommand.Standby -> {
                VoiceHandsPsychGate.bound.value?.goHub()
                enterStandby()
            }
            else -> {
                val matched = matchSpokenTopic(spoken)
                if (matched != null) askConfirmTopic(matched) else Unit
            }
        }
    }

    private suspend fun handleNameTopic(spoken: String) {
        when (VoiceHandsPhrases.matchCommand(spoken)) {
            VoiceHandsCommand.Standby -> {
                VoiceHandsPsychGate.bound.value?.goHub()
                enterStandby()
            }
            VoiceHandsCommand.ListTopics -> startTopicListing()
            VoiceHandsCommand.NoTopic -> applyTopicChoice(VoiceTopicChoice(displayName = "без темы"))
            VoiceHandsCommand.Confirm,
            VoiceHandsCommand.NameTopic,
            VoiceHandsCommand.Start,
            VoiceHandsCommand.Done,
            VoiceHandsCommand.Analyze,
            VoiceHandsCommand.Recommend,
            VoiceHandsCommand.Work,
            VoiceHandsCommand.Read -> Unit
            null -> {
                val cleaned = VoiceHandsTopicSuggest.stripTopicPrefix(spoken)
                    .ifBlank { VoiceHandsPhrases.normalize(spoken) }
                if (cleaned.isBlank()) {
                    say(VoiceHandsRu.SAY_NAME_TOPIC)
                    return
                }
                val existing = VoiceHandsTopicSuggest.matchTopic(cleaned, topicCatalogCache)
                val choice = if (existing != null) {
                    VoiceTopicChoice(topicId = existing.id, displayName = existing.name)
                } else {
                    val named = cleaned.replaceFirstChar { it.uppercaseChar() }.take(40).trim()
                    VoiceTopicChoice(createName = named, displayName = named)
                }
                askConfirmTopic(choice)
            }
        }
    }

    private suspend fun askNameTopic() {
        listingTopics = false
        listJob?.cancel()
        say(VoiceHandsRu.SAY_NAME_TOPIC)
        setPhase(VoiceHandsPhase.NameTopic)
        listenForPhase(VoiceHandsPhase.NameTopic)
    }

    private suspend fun askConfirmTopic(choice: VoiceTopicChoice) {
        listingTopics = false
        listJob?.cancel()
        bargeTopicChoice = null
        pendingTopicChoice = choice
        say("Вы выбрали тему «${choice.displayName}». Подтверждаете?")
        setPhase(VoiceHandsPhase.ConfirmTopic)
        listenForPhase(VoiceHandsPhase.ConfirmTopic)
    }

    private fun matchSpokenTopic(spoken: String): VoiceTopicChoice? {
        val cleaned = VoiceHandsTopicSuggest.stripTopicPrefix(spoken)
            .ifBlank { VoiceHandsPhrases.normalize(spoken) }
        if (cleaned.isBlank()) return null
        val existing = VoiceHandsTopicSuggest.matchTopic(cleaned, topicCatalogCache)
            ?: VoiceHandsTopicSuggest.matchTopic(spoken, topicCatalogCache)
        return existing?.let { VoiceTopicChoice(topicId = it.id, displayName = it.name) }
    }

    private fun startTopicListing() {
        if (_ui.value.phase == VoiceHandsPhase.ListTopics && listingTopics) return
        thinkJob?.cancel()
        listJob?.cancel()
        bargeTopicChoice = null
        bargeCommand = null
        setPhase(VoiceHandsPhase.ListTopics)
        listJob = scope.launch { runTopicListing() }
    }

    private suspend fun runTopicListing() {
        listingTopics = true
        bargeTopicChoice = null
        listener.listenLoop(longSilence = false)
        val topics = topicCatalogCache.ifEmpty {
            VoiceHandsPsychGate.bound.value?.topicCatalog().orEmpty().also { topicCatalogCache = it }
        }
        if (topics.isEmpty()) {
            listingTopics = false
            say(VoiceHandsRu.SAY_LIST_TOPICS_EMPTY)
            setPhase(VoiceHandsPhase.SuggestTopic)
            listenForPhase(VoiceHandsPhase.SuggestTopic)
            return
        }
        sayKeepListening("Перечисляю темы.")
        for (topic in topics) {
            if (bargeTopicChoice != null || bargeCommand != null) break
            listingEcho = VoiceHandsPhrases.normalize(topic.name)
            sayKeepListening(topic.name)
            listingEcho = ""
            if (bargeTopicChoice != null || bargeCommand != null) break
            waitForTopicBarge(1_200)
        }
        listingEcho = ""
        val chosen = bargeTopicChoice
        val cmd = bargeCommand
        bargeTopicChoice = null
        bargeCommand = null
        listingTopics = false
        when {
            chosen != null -> askConfirmTopic(chosen)
            cmd == VoiceHandsCommand.NameTopic -> askNameTopic()
            cmd == VoiceHandsCommand.NoTopic -> applyTopicChoice(VoiceTopicChoice(displayName = "без темы"))
            cmd == VoiceHandsCommand.Standby -> {
                VoiceHandsPsychGate.bound.value?.goHub()
                enterStandby()
            }
            settings.enabled.value && _ui.value.phase == VoiceHandsPhase.ListTopics -> {
                say("Назовите тему или скажите «назову свою тему».")
                listenForPhase(VoiceHandsPhase.ListTopics)
            }
        }
    }

    private suspend fun waitForTopicBarge(ms: Long) {
        var left = ms
        while (left > 0 && bargeTopicChoice == null && bargeCommand == null) {
            val step = 100L.coerceAtMost(left)
            delay(step)
            left -= step
        }
    }

    private fun maybeTopicBargeIn(text: String) {
        if (!listingTopics) return
        when (val cmd = VoiceHandsPhrases.matchCommand(text)) {
            VoiceHandsCommand.NameTopic,
            VoiceHandsCommand.NoTopic,
            VoiceHandsCommand.Standby -> {
                bargeCommand = cmd
                speaker.stop()
                return
            }
            else -> Unit
        }
        val matched = matchSpokenTopic(text) ?: return
        bargeTopicChoice = matched
        speaker.stop()
    }

    private suspend fun applyPendingTopic() {
        val choice = pendingTopicChoice
        if (choice == null || choice.isEmpty) {
            say("Сначала выберите тему. ${VoiceHandsRu.SAY_TOPIC_HINT}")
            setPhase(VoiceHandsPhase.SuggestTopic)
            listenForPhase(VoiceHandsPhase.SuggestTopic)
            return
        }
        applyTopicChoice(choice)
    }

    private suspend fun applyTopicChoice(choice: VoiceTopicChoice) {
        val situationId = topicSituationId ?: return
        val psych = VoiceHandsPsychGate.bound.value ?: return
        listener.stop()
        speaker.stop()
        listingTopics = false
        listJob?.cancel()
        pendingTopicChoice = choice
        suppressReviewOffer = true
        say(VoiceHandsRu.SAY_THINKING)
        beginThinking(VoiceHandsPhase.ThinkingQuestion)
        if (choice.displayName == "без темы") {
            psych.confirmTopicForVoice(situationId, null, null)
        } else {
            val create = choice.createName?.takeIf { choice.topicId == null }
            psych.confirmTopicForVoice(situationId, choice.topicId, create)
        }
        val ready = withTimeoutOrNull(8_000) {
            psych.ui.first { it.isReview || it.isIdle || it.isPaywall || !it.error.isNullOrBlank() }
        }
        thinkJob?.cancel()
        suppressReviewOffer = false
        when {
            ready == null -> {
                say(VoiceHandsRu.SAY_TIMEOUT)
                setPhase(VoiceHandsPhase.SuggestTopic)
                listenForPhase(VoiceHandsPhase.SuggestTopic)
            }
            ready.isPaywall -> {
                say(VoiceHandsRu.psychMissing)
                enterStandby()
            }
            !ready.error.isNullOrBlank() -> {
                say(ready.error)
                setPhase(VoiceHandsPhase.SuggestTopic)
                listenForPhase(VoiceHandsPhase.SuggestTopic)
            }
            else -> startOfferActions()
        }
    }

    private suspend fun runAiCommand(kind: String?, block: (VoiceHandsPsych) -> Unit) {
        val psych = VoiceHandsPsychGate.bound.value ?: return
        // Keep the current Result marked handled so we don't re-offer reading
        // the old analyze text while recommend (or another kind) is still loading.
        handledResultKey = psych.ui.value.resultKey
        pendingRead = ""
        expectedResultKind = kind
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

    private fun startOfferActions() {
        if (_ui.value.phase == VoiceHandsPhase.OfferActions) return
        thinkJob?.cancel()
        listJob?.cancel()
        bargeCommand = null
        bargeTopicChoice = null
        listingTopics = false
        offerArmed = true
        setPhase(VoiceHandsPhase.OfferActions)
        listJob = scope.launch { runOfferListing() }
    }

    private suspend fun runOfferListing() {
        listing = true
        bargeCommand = null
        listener.listenLoop(longSilence = false)
        val chunks = listOf(
            VoiceHandsRu.SAY_SITUATION_CLEAR,
            VoiceHandsRu.SAY_CMD_ANALYZE,
            VoiceHandsRu.SAY_CMD_RECOMMEND,
            VoiceHandsRu.SAY_CMD_WORK,
            VoiceHandsRu.SAY_CMD_STANDBY
        )
        for (line in chunks) {
            if (bargeCommand != null) break
            listingEcho = VoiceHandsPhrases.normalize(line)
            sayKeepListening(line)
            listingEcho = ""
            if (bargeCommand != null) break
            waitForBarge(1_200)
        }
        listingEcho = ""
        val chosen = bargeCommand
        bargeCommand = null
        listing = false
        if (chosen != null) {
            activateAction(chosen)
        } else if (settings.enabled.value && _ui.value.phase == VoiceHandsPhase.OfferActions) {
            listenForPhase(VoiceHandsPhase.OfferActions)
        }
    }

    private suspend fun waitForBarge(ms: Long) {
        var left = ms
        while (left > 0 && bargeCommand == null) {
            val step = 100L.coerceAtMost(left)
            delay(step)
            left -= step
        }
    }

    private suspend fun handleOffer(spoken: String) {
        if (listing) {
            maybeBargeIn(spoken)
            return
        }
        when (val cmd = VoiceHandsPhrases.matchCommand(spoken)) {
            VoiceHandsCommand.Analyze,
            VoiceHandsCommand.Recommend,
            VoiceHandsCommand.Work,
            VoiceHandsCommand.Standby -> activateAction(cmd)
            else -> Unit
        }
    }

    private fun maybeBargeIn(text: String) {
        if (!listing || !offerArmed) return
        val cmd = VoiceHandsPhrases.matchCommand(text) ?: return
        if (cmd !in ACTION_COMMANDS) return
        if (speaker.speaking.value) {
            val echo = listingEcho
            val heard = VoiceHandsPhrases.normalize(text)
            if (echo.isNotBlank() && (heard.contains(echo) || echo.contains(heard))) return
        }
        bargeCommand = cmd
        speaker.stop()
    }

    private suspend fun activateAction(cmd: VoiceHandsCommand) {
        if (!offerArmed) return
        offerArmed = false
        listing = false
        listener.stop()
        speaker.stop()
        when (cmd) {
            VoiceHandsCommand.Analyze,
            VoiceHandsCommand.Recommend,
            VoiceHandsCommand.Work -> {
                if (VoiceHandsPsychGate.bound.value == null) {
                    offerArmed = true
                    listenForPhase(VoiceHandsPhase.OfferActions)
                    return
                }
                when (cmd) {
                    VoiceHandsCommand.Analyze -> runAiCommand("analyze") { it.analyze() }
                    VoiceHandsCommand.Recommend -> runAiCommand("recommend") { it.recommend() }
                    else -> runAiCommand(null) { it.startWork() }
                }
            }
            VoiceHandsCommand.Standby -> {
                VoiceHandsPsychGate.bound.value?.goHub()
                enterStandby()
            }
            else -> {
                offerArmed = true
                listenForPhase(VoiceHandsPhase.OfferActions)
            }
        }
    }

    private suspend fun sayKeepListening(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || bargeCommand != null || bargeTopicChoice != null) return
        speaker.stop()
        publish { copy(speaking = true) }
        speaker.speak(clean)
        delay(100)
        val began = withTimeoutOrNull(1_000) { speaker.speaking.first { it } }
        if (began == true) {
            withTimeoutOrNull(20_000) { speaker.speaking.first { !it } }
        }
        if (speaker.speaking.value) speaker.stop()
        publish { copy(speaking = false) }
    }

    private suspend fun onPsychUi(psych: VoiceHandsPsych, ui: PsychUi) {
        val follow = mutex.withLock { inspectPsych(psych, ui) } ?: return
        thinkJob?.cancel()
        if (follow.kind == FollowKind.OfferActions) {
            startOfferActions()
            return
        }
        if (follow.kind == FollowKind.SuggestTopic) {
            val situationId = follow.text.toLongOrNull() ?: return
            scope.launch { mutex.withLock { beginSuggestTopic(situationId) } }
            return
        }
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
                FollowKind.OfferActions -> Unit
                FollowKind.SuggestTopic -> Unit
            }
        }
    }

    private fun inspectPsych(psych: VoiceHandsPsych, ui: PsychUi): FollowUp? {
        return when (_ui.value.phase) {
            VoiceHandsPhase.ThinkingQuestion -> {
                if (ui.isReview) {
                    if (suppressReviewOffer) return null
                    return FollowUp(FollowKind.OfferActions, "")
                }
                if (ui.isPaywall) {
                    return FollowUp(FollowKind.Error, VoiceHandsRu.psychMissing)
                }
                if (!ui.error.isNullOrBlank() && !ui.waiting) {
                    return FollowUp(FollowKind.Error, ui.error)
                }
                val situationId = ui.topicPickId
                if (situationId != null && !skippedTopicPick) {
                    skippedTopicPick = true
                    return FollowUp(FollowKind.SuggestTopic, situationId.toString())
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
                // Page still shows the previous Result while AI runs — wait for the new one.
                if (ui.waiting) return null
                val expected = expectedResultKind
                if (expected != null && ui.resultKind != expected) return null
                val key = ui.resultKey
                val speakable = ui.resultSpeakable
                if (key != null && speakable != null && handledResultKey != key) {
                    handledResultKey = key
                    expectedResultKind = null
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
            val phase = mutex.withLock { _ui.value.phase }
            if (phase != VoiceHandsPhase.ThinkingQuestion && phase != VoiceHandsPhase.ThinkingResult) {
                return@launch
            }
            speaker.stop()
            if (phase == VoiceHandsPhase.ThinkingQuestion) {
                val review = VoiceHandsPsychGate.bound.value?.ui?.value?.isReview == true
                if (review) {
                    startOfferActions()
                    return@launch
                }
            }
            say(VoiceHandsRu.SAY_TIMEOUT)
            mutex.withLock {
                val now = _ui.value.phase
                if (now == VoiceHandsPhase.ThinkingQuestion || now == VoiceHandsPhase.ThinkingResult) {
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
            VoiceHandsPhase.AfterRead,
            VoiceHandsPhase.OfferActions,
            VoiceHandsPhase.SuggestTopic,
            VoiceHandsPhase.ListTopics,
            VoiceHandsPhase.ConfirmTopic,
            VoiceHandsPhase.NameTopic -> startListen(phase)
            else -> listener.stop()
        }
    }

    private fun startListen(phase: VoiceHandsPhase) {
        val long = phase == VoiceHandsPhase.Dictating || phase == VoiceHandsPhase.AwaitingReply
        when (phase) {
            VoiceHandsPhase.Standby,
            VoiceHandsPhase.AskRead,
            VoiceHandsPhase.AfterRead,
            VoiceHandsPhase.ConfirmTopic -> listener.listenOnce(longSilence = false)
            VoiceHandsPhase.OfferActions,
            VoiceHandsPhase.SuggestTopic,
            VoiceHandsPhase.ListTopics,
            VoiceHandsPhase.NameTopic -> listener.listenLoop(longSilence = false)
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
        VoiceHandsPhase.SuggestTopic,
        VoiceHandsPhase.ListTopics,
        VoiceHandsPhase.ConfirmTopic,
        VoiceHandsPhase.NameTopic -> VoiceHandsRu.offerActions
        VoiceHandsPhase.AwaitingReply -> VoiceHandsRu.awaiting
        VoiceHandsPhase.AskRead -> VoiceHandsRu.askRead
        VoiceHandsPhase.Reading -> VoiceHandsRu.reading
        VoiceHandsPhase.AfterRead -> VoiceHandsRu.afterRead
        VoiceHandsPhase.OfferActions -> VoiceHandsRu.offerActions
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

    private enum class FollowKind { Question, ReadyRead, Error, OfferActions, SuggestTopic }

    private data class FollowUp(
        val kind: FollowKind,
        val text: String,
        val speakable: String = ""
    )

    companion object {
        private const val THINK_TIMEOUT_MS = 60_000L
        private val ACTION_COMMANDS = setOf(
            VoiceHandsCommand.Analyze,
            VoiceHandsCommand.Recommend,
            VoiceHandsCommand.Work,
            VoiceHandsCommand.Standby
        )
    }
}
