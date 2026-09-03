package ru.na.step4.obidy.ui.psych

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.psych.PsychAiClient
import ru.na.step4.obidy.data.psych.PsychInboxMessage
import ru.na.step4.obidy.data.psych.PsychLocks
import ru.na.step4.obidy.data.psych.PsychLogic
import ru.na.step4.obidy.data.psych.PsychQa
import ru.na.step4.obidy.data.psych.PsychRepository
import ru.na.step4.obidy.data.psych.PsychRu
import ru.na.step4.obidy.data.psych.PsychSession
import ru.na.step4.obidy.data.psych.PsychSettings
import ru.na.step4.obidy.data.psych.PsychSituation
import ru.na.step4.obidy.data.psych.PsychTeaserStore
import ru.na.step4.obidy.data.psych.PsychTopic
import ru.na.step4.obidy.data.psych.PsychTopicStory
import ru.na.step4.obidy.data.spiritual.SpiritualRatingStore
import ru.na.step4.obidy.data.spiritual.SpiritualSource

sealed class PsychPage {
    data object Hub : PsychPage()
    data class Onboarding(val hint: String? = null) : PsychPage()
    data object Record : PsychPage()
    data class TopicPick(
        val situationId: Long,
        val showAll: Boolean = false,
        val selectedIds: Set<Long> = emptySet(),
        val snippets: Map<Long, String> = emptyMap()
    ) : PsychPage()
    data class Dialogue(
        val situation: PsychSituation,
        val session: PsychSession,
        val question: String,
        val answers: List<PsychQa>,
        val prompt: String = ""
    ) : PsychPage()
    data class Result(
        val situation: PsychSituation,
        val session: PsychSession,
        val kind: String,
        val text: String,
        val speakable: String,
        val teaser: Boolean,
        val teaserKey: String = "",
        val prompt: String = ""
    ) : PsychPage()
    data class Work(
        val situation: PsychSituation,
        val session: PsychSession,
        val questions: List<String>,
        val index: Int,
        val answers: List<PsychQa>,
        val prompt: String = ""
    ) : PsychPage()
    data class Done(
        val situation: PsychSituation,
        val session: PsychSession,
        val answers: List<PsychQa>
    ) : PsychPage()
    data class ViewPeriod(
        val week: Boolean,
        val from: Long,
        val to: Long,
        val items: List<ViewItem>,
        val asOneText: Boolean
    ) : PsychPage()
    data object Settings : PsychPage()
    data object Profile : PsychPage()
    data object AiSettings : PsychPage()
    data object Topics : PsychPage()
    data class TopicDetail(
        val topic: PsychTopic,
        val stories: List<PsychTopicStory> = emptyList()
    ) : PsychPage()
    data object Reminders : PsychPage()
    data class Paywall(val reason: String) : PsychPage()
    data class SessionList(val postponed: Boolean) : PsychPage()
    data class Idle(val situation: PsychSituation, val session: PsychSession, val work: Boolean) : PsychPage()
    data class Review(
        val situation: PsychSituation,
        val session: PsychSession,
        val answers: List<PsychQa>
    ) : PsychPage()
}

data class ViewItem(
    val situation: PsychSituation,
    val session: PsychSession?,
    val answers: List<PsychQa>,
    val timeLabel: String
)

data class PsychUi(
    val page: PsychPage = PsychPage.Hub,
    val waiting: Boolean = false,
    val waitKind: String = "answer",
    val spinner: Int = 0,
    val error: String? = null,
    val quotaLine: String? = null,
    val upsell: String? = null,
    val outreach: String? = null,
    val speaking: Boolean = false,
    val topicSnippets: Map<Long, String> = emptyMap(),
    val inbox: List<PsychInboxMessage> = emptyList()
)

data class PremiumPayUi(
    val busy: Boolean = false,
    val awaitingReturn: Boolean = false,
    val paymentsEnabled: Boolean = false,
    val message: String? = null,
    val lastPaymentId: String? = null
)

class PsychViewModel(
    private val repository: PsychRepository,
    val settings: PsychSettings,
    private val spiritual: SpiritualRatingStore? = null,
    private val journalPrefs: ru.na.step4.obidy.data.journal.JournalPrefs? = null,
    private val activityLog: ru.na.step4.obidy.data.activity.ActivityLog? = null
) : ViewModel() {
    private val client = PsychAiClient()
    private val _ui = MutableStateFlow(PsychUi())
    val ui: StateFlow<PsychUi> = _ui.asStateFlow()

    private val _premiumPay = MutableStateFlow(PremiumPayUi())
    val premiumPay: StateFlow<PremiumPayUi> = _premiumPay.asStateFlow()

    val isAdmin: Boolean
        get() = journalPrefs?.isAdmin == true

    val topics = repository.observeTopics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val postponed = repository.observePostponed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val completed = repository.observeCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var spinnerJob: Job? = null
    private var premiumPollJob: Job? = null
    private var lastFullText: String = ""
    private var lastSpeakable: String = ""
    private var inboxWatch: SharedPreferences.OnSharedPreferenceChangeListener? = null

    init {
        settings.expireProIfNeeded()
        if (!settings.onboardingDone) {
            if (settings.name.isNotBlank()) {
                settings.onboardingDone = true
            } else {
                _ui.value = PsychUi(page = PsychPage.Onboarding())
            }
        }
        reloadInbox()
        inboxWatch = settings.watchInbox {
            viewModelScope.launch { reloadInbox() }
        }
        viewModelScope.launch { maybeIdle() }
        viewModelScope.launch { refreshPremiumFromServer(silent = true) }
        viewModelScope.launch { applyServerQuestionLimits() }
    }

    override fun onCleared() {
        inboxWatch?.let { settings.unwatchInbox(it) }
        inboxWatch = null
        super.onCleared()
    }

    private suspend fun applyServerQuestionLimits() {
        val cfg = withContext(Dispatchers.IO) {
            ru.na.step4.obidy.data.ai.AppConfigClient.fetch()
        }
        settings.applyAdminQuestionLimits(cfg.psychDialogueExtra, cfg.psychWorkQuestions)
        bump()
    }

    private fun reloadInbox() {
        _ui.value = _ui.value.copy(inbox = settings.inboxMessages())
    }

    fun goHub() = setPage(PsychPage.Hub)

    fun goRecord() = setPage(PsychPage.Record)

    fun goSettings() = setPage(PsychPage.Settings)

    fun goProfile() = setPage(PsychPage.Profile)

    fun goAi() = setPage(PsychPage.AiSettings)

    fun goTopics() {
        viewModelScope.launch {
            refreshSnippets()
            setPage(PsychPage.Topics)
        }
    }

    fun goReminders() = setPage(PsychPage.Reminders)

    fun goPaywall(reason: String = "pro") = setPage(PsychPage.Paywall(reason))

    fun showAllTopics(situationId: Long) {
        val page = _ui.value.page as? PsychPage.TopicPick
        setPage((page ?: PsychPage.TopicPick(situationId)).copy(showAll = true))
    }

    fun goSessions(postponedList: Boolean) = setPage(PsychPage.SessionList(postponedList))

    fun skipOnboarding() {
        settings.onboardingDone = true
        settings.appendInbox(PsychRu.meetNice.format(PsychRu.skipName))
        _ui.value = PsychUi(
            page = PsychPage.Record,
            inbox = settings.inboxMessages()
        )
    }

    fun submitOnboardingName(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) {
            skipOnboarding()
            return
        }
        if (PsychLogic.looksLikeSituationName(text)) {
            _ui.value = _ui.value.copy(page = PsychPage.Onboarding(hint = PsychRu.nameTooLong))
            return
        }
        settings.name = text.take(40)
        settings.onboardingDone = true
        settings.appendInbox(PsychRu.meetNice.format(settings.name))
        _ui.value = PsychUi(
            page = PsychPage.Record,
            inbox = settings.inboxMessages()
        )
    }

    fun submitSituation(text: String, viaVoice: Boolean = false) {
        val body = text.trim()
        if (body.isEmpty()) return
        if (settings.reminderOutreachPending && PsychLogic.looksLikeReadiness(body)) {
            settings.reminderOutreachPending = false
            settings.appendInbox(PsychRu.describe)
            _ui.value = _ui.value.copy(page = PsychPage.Record, inbox = settings.inboxMessages())
            return
        }
        settings.reminderOutreachPending = false
        viewModelScope.launch {
            val id = repository.saveSituation(body, viaVoice, noHistory = false, topicId = null)
            if (settings.topicsEnabled) {
                val snippets = topicSnippets()
                setPage(PsychPage.TopicPick(id, snippets = snippets))
            } else {
                startLive(id, noHistory = false)
            }
        }
    }

    fun pickTopic(situationId: Long, topicId: Long?, noHistory: Boolean) {
        viewModelScope.launch {
            if (noHistory) {
                repository.setNoHistory(situationId, true)
                repository.attachTopic(situationId, null)
                startLive(situationId, noHistory = true)
            } else {
                repository.attachTopic(situationId, topicId)
                startLive(situationId, noHistory = false)
            }
        }
    }

    fun addTopicAndPick(situationId: Long, name: String) {
        viewModelScope.launch {
            val id = repository.addTopic(name)
            if (id == 0L) return@launch
            val page = _ui.value.page as? PsychPage.TopicPick
                ?: PsychPage.TopicPick(situationId)
            val snippets = topicSnippets()
            setPage(
                page.copy(
                    situationId = situationId,
                    selectedIds = page.selectedIds + id,
                    snippets = snippets,
                    showAll = true
                )
            )
        }
    }

    fun toggleTopicSelection(topicId: Long) {
        val page = _ui.value.page as? PsychPage.TopicPick ?: return
        val next = if (topicId in page.selectedIds) {
            page.selectedIds - topicId
        } else {
            page.selectedIds + topicId
        }
        setPage(page.copy(selectedIds = next))
    }

    fun confirmSelectedTopics() {
        val page = _ui.value.page as? PsychPage.TopicPick ?: return
        viewModelScope.launch {
            repository.attachTopics(page.situationId, page.selectedIds.toList())
            startLive(page.situationId, noHistory = false)
        }
    }

    fun answerDialogue(text: String, viaVoice: Boolean = false) {
        val page = _ui.value.page as? PsychPage.Dialogue ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            val nextIndex = page.answers.size
            repository.saveAnswer(page.session.sessionUid, nextIndex, page.question, body, viaVoice)
            val answers = repository.qaFor(page.session.sessionUid)
            val updated = page.session.copy(currentIndex = answers.size)
            repository.updateSession(updated)
            if (answers.size >= settings.dialogueExtraLimit) {
                setPage(PsychPage.Review(page.situation, updated, answers))
                return@launch
            }
            setPage(PsychPage.Dialogue(page.situation, updated, question = "", answers, prompt = page.prompt))
            askDialogueQuestion(page.situation, updated, answers)
        }
    }

    fun analyze() = closeDialogueAndAi("analyze")
    fun recommend() = closeDialogueAndAi("recommend")

    fun startWork() {
        val (situation, session) = currentSituationSession() ?: return
        viewModelScope.launch {
            val sit = repository.getSituation(situation.id) ?: situation
            val seq = if (settings.isPro) PsychSession.SEQ_PRO else PsychSession.SEQ_BATCH
            val work = if (session.status == PsychSession.STATUS_DONE) {
                val uid = settings.newSessionUid()
                val created = repository.createSession(sit.id, uid, seq)
                settings.activeSessionUid = uid
                created
            } else {
                closeLive(session)
                val updated = session.copy(
                    sequentialWork = seq,
                    status = PsychSession.STATUS_ACTIVE,
                    postponed = false,
                    currentIndex = 0
                )
                repository.updateSession(updated)
                settings.activeSessionUid = updated.sessionUid
                updated
            }
            val answers = repository.qaFor(work.sessionUid)
            setPage(PsychPage.Work(sit, work, emptyList(), 0, answers))
            if (seq == PsychSession.SEQ_PRO) {
                askNextWorkQuestion(sit, work, answers)
            } else {
                requestQuestions(sit, work, answers)
            }
        }
    }

    fun answerWork(text: String, viaVoice: Boolean = false) {
        val page = _ui.value.page as? PsychPage.Work ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            repository.saveAnswer(
                page.session.sessionUid,
                page.index,
                page.questions.getOrElse(page.index) { page.questions.lastOrNull().orEmpty() },
                body,
                viaVoice
            )
            val answers = repository.qaFor(page.session.sessionUid)
            val next = page.index + 1
            if (page.session.sequentialWork == PsychSession.SEQ_PRO) {
                if (next >= settings.workQuestionLimit) {
                    complete(page.situation, page.session, answers)
                } else {
                    val updated = page.session.copy(currentIndex = next)
                    repository.updateSession(updated)
                    setPage(
                        PsychPage.Work(
                            page.situation,
                            updated,
                            page.questions,
                            next,
                            answers,
                            prompt = page.prompt
                        )
                    )
                    askNextWorkQuestion(page.situation, updated, answers)
                }
            } else {
                if (next >= page.questions.size) {
                    complete(page.situation, page.session, answers)
                } else {
                    val updated = page.session.copy(currentIndex = next)
                    repository.updateSession(updated)
                    setPage(
                        PsychPage.Work(
                            page.situation,
                            updated,
                            page.questions,
                            next,
                            answers,
                            prompt = page.prompt
                        )
                    )
                }
            }
        }
    }

    fun postpone() {
        val (situation, session) = currentSituationSession() ?: return
        viewModelScope.launch {
            repository.updateSession(
                session.copy(postponed = true, status = PsychSession.STATUS_ACTIVE)
            )
            settings.activeSessionUid = ""
            setPage(PsychPage.Hub)
        }
    }

    fun finishNow() {
        viewModelScope.launch {
            val pair = currentSituationSession() ?: return@launch
            val answers = repository.qaFor(pair.second.sessionUid)
            complete(pair.first, pair.second, answers)
        }
    }

    fun assistant() {
        val page = _ui.value.page
        viewModelScope.launch {
            val situation: PsychSituation
            val session: PsychSession
            when (page) {
                is PsychPage.Done -> {
                    situation = page.situation
                    session = page.session
                }
                is PsychPage.Result -> {
                    situation = page.situation
                    session = page.session
                }
                else -> {
                    val pair = currentSituationSession() ?: return@launch
                    situation = pair.first
                    session = pair.second
                }
            }
            val answers = repository.qaFor(session.sessionUid)
            callAi("assistant", situation, session, answers) { ok ->
                val updated = session.copy(assistantText = ok.text, assistantSpeakable = ok.speakable)
                repository.updateSession(updated)
                setPage(
                    PsychPage.Result(
                        situation,
                        updated,
                        "assistant",
                        displayText("assistant", ok.text),
                        ok.speakable,
                        teaser = false,
                        prompt = ok.prompt
                    )
                )
            }
        }
    }

    fun readMore() {
        if (!settings.isPro) {
            val key = settings.pendingReadMoreKey
            setPage(PsychPage.Paywall("read_more:$key"))
            return
        }
        revealPendingFull()
    }

    fun afterProUnlocked() {
        settings.expireProIfNeeded()
        if (settings.isPro) revealPendingFull()
        else goHub()
    }

    fun grantDebugPro() {
        settings.grantProDays(30)
        journalPrefs?.isPro = true
        afterProUnlocked()
    }

    fun startPremiumPayment(onUrl: (String) -> Unit) {
        val deviceId = journalPrefs?.deviceId.orEmpty()
        if (deviceId.isBlank()) {
            _premiumPay.value = _premiumPay.value.copy(message = "Нет идентификатора устройства")
            return
        }
        if (_premiumPay.value.busy) return
        viewModelScope.launch {
            _premiumPay.value = _premiumPay.value.copy(
                busy = true,
                message = PsychRu.paywallOpening
            )
            val returnUrl = "https://12stepsapp.luch-rehab.ru/premium/return"
            val raw = withContext(Dispatchers.IO) {
                ru.na.step4.obidy.data.ai.PremiumClient.createPayment(deviceId, returnUrl)
            }
            val (created, err) = ru.na.step4.obidy.data.ai.PremiumClient.parseCreate(raw)
            if (created == null) {
                _premiumPay.value = _premiumPay.value.copy(busy = false, message = err)
                return@launch
            }
            _premiumPay.value = _premiumPay.value.copy(
                busy = false,
                awaitingReturn = true,
                paymentsEnabled = true,
                lastPaymentId = created.paymentId,
                message = PsychRu.paywallWaiting
            )
            onUrl(created.confirmationUrl)
            startPremiumPoll(created.paymentId)
        }
    }

    fun onPremiumReturn() {
        val paymentId = _premiumPay.value.lastPaymentId
        if (paymentId.isNullOrBlank() && !_premiumPay.value.awaitingReturn) {
            refreshPremiumFromServer(silent = false)
            return
        }
        startPremiumPoll(paymentId)
    }

    fun refreshPremiumFromServer(silent: Boolean = true) {
        val deviceId = journalPrefs?.deviceId.orEmpty()
        if (deviceId.isBlank()) return
        viewModelScope.launch {
            val paymentId = _premiumPay.value.lastPaymentId
            val raw = withContext(Dispatchers.IO) {
                ru.na.step4.obidy.data.ai.PremiumClient.status(deviceId, paymentId)
            }
            val (status, err) = ru.na.step4.obidy.data.ai.PremiumClient.parseStatus(raw)
            if (status == null) {
                if (!silent) {
                    _premiumPay.value = _premiumPay.value.copy(message = err)
                }
                return@launch
            }
            _premiumPay.value = _premiumPay.value.copy(paymentsEnabled = true)
            if (applyServerEntitlement(status)) {
                premiumPollJob?.cancel()
                _premiumPay.value = _premiumPay.value.copy(
                    awaitingReturn = false,
                    message = PsychRu.paywallThanks
                )
                afterProUnlocked()
            } else if (!silent && _premiumPay.value.awaitingReturn) {
                _premiumPay.value = _premiumPay.value.copy(message = PsychRu.paywallWaiting)
            }
        }
    }

    private fun startPremiumPoll(paymentId: String?) {
        premiumPollJob?.cancel()
        val deviceId = journalPrefs?.deviceId.orEmpty()
        if (deviceId.isBlank()) return
        premiumPollJob = viewModelScope.launch {
            repeat(40) {
                delay(3_000)
                val raw = withContext(Dispatchers.IO) {
                    ru.na.step4.obidy.data.ai.PremiumClient.status(deviceId, paymentId)
                }
                val (status, _) = ru.na.step4.obidy.data.ai.PremiumClient.parseStatus(raw)
                if (status != null && applyServerEntitlement(status)) {
                    _premiumPay.value = _premiumPay.value.copy(
                        awaitingReturn = false,
                        message = PsychRu.paywallThanks
                    )
                    afterProUnlocked()
                    return@launch
                }
            }
        }
    }

    private fun applyServerEntitlement(status: ru.na.step4.obidy.data.ai.PremiumClient.StatusResult): Boolean {
        if (!status.premium) return false
        val expires = status.expiresAtUnix * 1000L
        if (expires > settings.proExpiryMillis) {
            settings.proExpiryMillis = expires
        } else if (expires <= 0L) {
            settings.grantProDays(status.premiumDays.coerceAtLeast(1))
        }
        journalPrefs?.isPro = true
        return settings.isPro
    }

    fun openView(week: Boolean, dateMillis: Long? = null) {
        if (week && !settings.isPro) {
            setPage(PsychPage.Paywall("week"))
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val offset = settings.utcOffsetMinutes
            val range = if (week) {
                PsychLogic.weekRange(now, offset)
            } else {
                PsychLogic.dayRange(dateMillis ?: now, offset)
            }
            val sits = repository.situationsInRange(range.first, range.second)
            val items = sits.map { sit ->
                val session = repository.sessionForSituation(sit.id)
                val answers = session?.let { repository.qaFor(it.sessionUid) }.orEmpty()
                ViewItem(sit, session, answers, PsychLogic.formatLocal(sit.createdAt, offset))
            }
            setPage(
                PsychPage.ViewPeriod(
                    week = week,
                    from = range.first,
                    to = range.second,
                    items = items,
                    asOneText = false
                )
            )
        }
    }

    fun toggleViewMode() {
        val page = _ui.value.page as? PsychPage.ViewPeriod ?: return
        setPage(page.copy(asOneText = !page.asOneText))
    }

    fun setViewAsOneText(value: Boolean) {
        val page = _ui.value.page as? PsychPage.ViewPeriod ?: return
        if (page.asOneText == value) return
        setPage(page.copy(asOneText = value))
    }

    fun openSituationReview(situationId: Long, sessionId: Long? = null) {
        viewModelScope.launch {
            val situation = repository.getSituation(situationId) ?: return@launch
            val session = when {
                sessionId != null -> repository.getSession(sessionId)
                else -> null
            } ?: repository.sessionForSituation(situationId)
                ?: repository.createSession(
                    situation.id,
                    settings.newSessionUid(),
                    PsychSession.SEQ_LIVE
                )
            val answers = repository.qaFor(session.sessionUid)
            settings.activeSessionUid = session.sessionUid
            setPage(PsychPage.Review(situation, session, answers))
        }
    }

    fun openSession(sessionId: Long) {
        viewModelScope.launch {
            val session = repository.getSession(sessionId) ?: return@launch
            val situation = repository.getSituation(session.situationId) ?: return@launch
            val answers = repository.qaFor(session.sessionUid)
            when {
                session.status == PsychSession.STATUS_DONE ->
                    setPage(PsychPage.Done(situation, session, answers))
                session.sequentialWork == PsychSession.SEQ_LIVE && session.currentIndex > 0 -> {
                    val lastQ = answers.lastOrNull()?.question
                        ?: repository.answers(session.sessionUid).lastOrNull()?.questionText
                        ?: ""
                    val pending = if (answers.size == session.currentIndex && lastQ.isNotBlank()) {
                        lastQ
                    } else {
                        answers.lastOrNull()?.question.orEmpty()
                    }
                    settings.activeSessionUid = session.sessionUid
                    setPage(PsychPage.Dialogue(situation, session.copy(postponed = false), pending.ifBlank { "…" }, answers))
                    if (pending.isBlank() || pending == "…") {
                        askDialogueQuestion(situation, session, answers)
                    }
                }
                session.sequentialWork != PsychSession.SEQ_LIVE -> {
                    val questions = PsychLogic.decodeQuestions(session.questionsJson)
                    setPage(
                        PsychPage.Work(
                            situation,
                            session.copy(postponed = false),
                            questions,
                            session.currentIndex,
                            answers
                        )
                    )
                }
                else -> setPage(PsychPage.Done(situation, session, answers))
            }
        }
    }

    fun shareText(): String? {
        val page = _ui.value.page
        return when (page) {
            is PsychPage.Done -> PsychLogic.shareText(
                page.situation.text, page.answers, page.situation.createdAt, settings.utcOffsetMinutes
            )
            is PsychPage.Dialogue -> PsychLogic.shareText(
                page.situation.text, page.answers, page.situation.createdAt, settings.utcOffsetMinutes
            )
            is PsychPage.Result -> buildString {
                appendLine(PsychLogic.shareText(page.situation.text, emptyList(), page.situation.createdAt, settings.utcOffsetMinutes))
                appendLine()
                appendLine(page.text)
            }
            is PsychPage.ViewPeriod -> page.items.joinToString("\n\n") { item ->
                PsychLogic.shareText(item.situation.text, item.answers, item.situation.createdAt, settings.utcOffsetMinutes)
            }
            is PsychPage.Review -> PsychLogic.shareText(
                page.situation.text, page.answers, page.situation.createdAt, settings.utcOffsetMinutes
            )
            else -> null
        }
    }

    fun continueIdle() {
        val page = _ui.value.page as? PsychPage.Idle ?: return
        viewModelScope.launch {
            val answers = repository.qaFor(page.session.sessionUid)
            setPage(PsychPage.Review(page.situation, page.session, answers))
        }
    }

    fun speakCurrent(kind: String) {
        if (!settings.isPro || !settings.voiceEnabled) {
            _ui.value = _ui.value.copy(error = PsychRu.notProVoice)
            return
        }
        val page = _ui.value.page as? PsychPage.Result ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(speaking = true, waiting = true, waitKind = "voice")
            val understanding = withContext(Dispatchers.IO) {
                client.request(
                    kind = "tts_understanding",
                    situation = page.situation.text,
                    answers = emptyList(),
                    settings = settings,
                    noHistory = true,
                    topic = null
                )
            }
            val understood = (understanding as? PsychAiClient.Result.Ok)?.text.orEmpty()
            val bodyIntro = when (kind) {
                "analyze" -> PsychRu.speakAnalyze
                "recommend" -> PsychRu.speakRecommend
                else -> PsychRu.speakAssistant
            }
            lastSpeakable = buildString {
                appendLine(PsychRu.speakIntro)
                appendLine(understood)
                appendLine(PsychRu.speakBridge)
                appendLine(bodyIntro)
                append(page.speakable.ifBlank { page.text })
            }
            _ui.value = _ui.value.copy(waiting = false, speaking = true)
        }
    }

    fun clearSpeaking() {
        _ui.value = _ui.value.copy(speaking = false)
        lastSpeakable = ""
    }

    fun consumeSpeakable(): String = lastSpeakable.ifBlank {
        (_ui.value.page as? PsychPage.Result)?.speakable.orEmpty()
    }

    fun enableReminders(on: Boolean) {
        settings.reminderEnabled = on
        if (on) {
            settings.nextReminderAt =
                System.currentTimeMillis() + settings.reminderIntervalHours * 3_600_000L
            viewModelScope.launch { refreshReminderText() }
        } else {
            settings.nextReminderAt = 0L
        }
        bump()
    }

    fun setReminderHours(hours: Int) {
        val clamped = hours.coerceIn(1, 72)
        if (!settings.isPro && clamped != 12) {
            setPage(PsychPage.Paywall("reminders"))
            return
        }
        settings.reminderIntervalHours = clamped
        if (settings.reminderEnabled) {
            settings.nextReminderAt =
                System.currentTimeMillis() + clamped * 3_600_000L
        }
        bump()
    }

    fun setQuietHours(startHour: Int, endHour: Int) {
        settings.quietStartHour = startHour.coerceIn(0, 23)
        settings.quietEndHour = endHour.coerceIn(0, 23)
        bump()
    }

    fun consumeNotificationOpen() {
        when (_ui.value.page) {
            is PsychPage.Onboarding,
            is PsychPage.Dialogue,
            is PsychPage.Work,
            is PsychPage.Result,
            is PsychPage.Done,
            is PsychPage.Idle,
            is PsychPage.Review -> return
            else -> goRecord()
        }
    }

    fun onRemindersShown() {
        if (settings.reminderEnabled && settings.nextReminderAt == 0L) {
            settings.nextReminderAt =
                System.currentTimeMillis() + settings.reminderIntervalHours * 3_600_000L
        }
        bump()
    }

    fun saveProfileField(field: String, value: String) {
        when (field) {
            "name" -> settings.name = value.trim().take(40)
            "birth" -> settings.birthYear = value.trim()
            "location" -> settings.location = value.trim()
            "program" -> settings.recoveryProgram = value.trim()
            "about" -> settings.aboutMe = value.trim()
            "offset" -> settings.utcOffsetMinutes = value.trim().toIntOrNull() ?: settings.utcOffsetMinutes
            "personality" -> settings.myPersonality = value.trim()
        }
        bump()
    }

    fun setQuestionLimits(dialogueExtra: Int?, workQuestions: Int?) {
        if (dialogueExtra != null) settings.dialogueExtraLimit = dialogueExtra
        if (workQuestions != null) settings.workQuestionLimit = workQuestions
        bump()
    }

    fun setAiOption(field: String, value: String) {
        val proFields = setOf("expanded", "critical", "hard", "long")
        if (value in proFields && !settings.isPro) {
            setPage(PsychPage.Paywall("ai"))
            return
        }
        when (field) {
            "variant" -> settings.aiResponseVariant = value
            "style" -> settings.aiResponseStyle = value
            "diff" -> settings.workQuestionDifficulty = value
            "len" -> settings.workQuestionLength = value
        }
        bump()
    }

    fun setPersonalityCollect(on: Boolean) {
        if (on && !settings.isPro) {
            setPage(PsychPage.Paywall("personality"))
            return
        }
        settings.personalityCollectEnabled = on
        bump()
    }

    fun setTopicsEnabled(on: Boolean) {
        settings.topicsEnabled = on
        bump()
    }

    fun setVoiceEnabled(on: Boolean) {
        settings.voiceEnabled = on
        bump()
    }

    fun createTopic(name: String) {
        viewModelScope.launch {
            repository.addTopic(name)
            refreshSnippets()
        }
    }

    fun openTopic(topic: PsychTopic) {
        viewModelScope.launch { showTopicDetail(topic.id) }
    }

    fun renameTopic(id: Long, name: String) {
        viewModelScope.launch {
            repository.renameTopic(id, name)
            showTopicDetail(id)
        }
    }

    fun deleteTopic(id: Long) {
        viewModelScope.launch {
            repository.deleteTopic(id)
            setPage(PsychPage.Topics)
        }
    }

    fun saveTopicSummary(id: Long, summary: String) {
        viewModelScope.launch {
            repository.updateTopicSummary(id, summary)
            showTopicDetail(id)
        }
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null, upsell = null, outreach = null)
    }

    private suspend fun startLive(
        situationId: Long,
        noHistory: Boolean,
        situation: PsychSituation? = null
    ) {
        val sit = situation ?: repository.getSituation(situationId) ?: return
        val flagged = sit.copy(noHistory = noHistory)
        val uid = settings.newSessionUid()
        val session = repository.createSession(flagged.id, uid, PsychSession.SEQ_LIVE)
        settings.activeSessionUid = uid
        activityLog?.start(
            ru.na.step4.obidy.data.activity.ActivityCat.PSYCH,
            ru.na.step4.obidy.data.activity.ActivityType.START,
            sit.text.take(80).ifBlank { "Психолог" },
            sessionKey = "psych-$uid"
        )
        setPage(PsychPage.Dialogue(flagged, session, question = "", answers = emptyList()))
        askDialogueQuestion(flagged, session, emptyList())
    }

    private fun closeDialogueAndAi(kind: String) {
        val (situation, session) = currentSituationSession() ?: return
        viewModelScope.launch {
            closeLive(session)
            val answers = repository.qaFor(session.sessionUid)
            callAi(kind, situation, session, answers) { ok ->
                var full = ok.text
                if (kind == "analyze" || kind == "recommend") {
                    spiritual?.applyTask(SpiritualSource.PSYCH)
                }
                if (kind == "analyze") {
                    full = spiritual?.consumeAiText(
                        "psych-${session.id}-analyze",
                        full
                    ) ?: ru.na.step4.obidy.data.spiritual.SpiritualDeltaParser.stripOnly(full)
                }
                lastFullText = full
                lastSpeakable = ok.speakable
                var teaser = false
                var shown = full
                var key = ""
                if (!settings.isPro && kind != "assistant") {
                    val split = PsychLogic.splitTeaser(full)
                    if (split.second != null) {
                        teaser = true
                        shown = split.first
                        key = PsychTeaserStore.put(full, ok.speakable)
                        settings.pendingReadMoreKey = key
                    }
                }
                val updated = when (kind) {
                    "analyze" -> session.copy(
                        sequentialWork = session.sequentialWork,
                        analyzeText = full,
                        analyzeSpeakable = ok.speakable,
                        status = PsychSession.STATUS_ACTIVE
                    )
                    "recommend" -> session.copy(
                        recommendText = full,
                        recommendSpeakable = ok.speakable
                    )
                    else -> session
                }
                repository.updateSession(updated)
                setPage(
                    PsychPage.Result(
                        situation,
                        updated,
                        kind,
                        displayText(kind, shown),
                        ok.speakable,
                        teaser,
                        key,
                        prompt = ok.prompt
                    )
                )
            }
        }
    }

    private suspend fun closeLive(session: PsychSession) {
        if (session.sequentialWork == PsychSession.SEQ_LIVE) {
            repository.updateSession(session.copy(status = PsychSession.STATUS_ACTIVE))
        }
        settings.activeSessionUid = session.sessionUid
    }

    private suspend fun requestQuestions(
        situation: PsychSituation,
        session: PsychSession,
        answers: List<PsychQa>
    ) {
        callAi("questions", situation, session, answers) { ok ->
            val questions = workQuestionsFrom(ok).filter { q ->
                answers.none { it.question.equals(q, ignoreCase = true) }
            }.ifEmpty { workQuestionsFrom(ok) }
                .take(settings.workQuestionLimit)
            if (questions.isEmpty()) {
                _ui.value = _ui.value.copy(error = Ru.analysisAiError)
                return@callAi
            }
            val updated = session.copy(
                questionsJson = PsychLogic.encodeQuestions(questions),
                currentIndex = 0,
                sequentialWork = PsychSession.SEQ_BATCH
            )
            repository.updateSession(updated)
            setPage(PsychPage.Work(situation, updated, questions, 0, answers, prompt = ok.prompt))
        }
    }

    private suspend fun askDialogueQuestion(
        situation: PsychSituation,
        session: PsychSession,
        answers: List<PsychQa>
    ) {
        if (answers.size >= settings.dialogueExtraLimit) {
            setPage(PsychPage.Review(situation, session, answers))
            return
        }
        callAi(
            "dialogue_question",
            situation,
            session,
            answers,
            questionNumber = answers.size + 1
        ) { ok ->
            val q = ok.question.ifBlank { ok.text }
            settings.lastQuestionAt = System.currentTimeMillis()
            val updated = session.copy(currentIndex = answers.size)
            repository.updateSession(updated)
            setPage(PsychPage.Dialogue(situation, updated, q, answers, prompt = ok.prompt))
        }
    }

    private suspend fun askNextWorkQuestion(
        situation: PsychSituation,
        session: PsychSession,
        answers: List<PsychQa>
    ) {
        if (answers.size >= settings.workQuestionLimit) {
            complete(situation, session, answers)
            return
        }
        callAi(
            "questions_next",
            situation,
            session,
            answers,
            questionNumber = answers.size + 1
        ) { ok ->
            val q = ok.question.ifBlank { ok.text }.trim()
            if (q.isBlank()) {
                _ui.value = _ui.value.copy(error = Ru.analysisAiError)
                return@callAi
            }
            val questions = PsychLogic.decodeQuestions(session.questionsJson) + q
            val updated = session.copy(
                questionsJson = PsychLogic.encodeQuestions(questions),
                currentIndex = answers.size,
                sequentialWork = PsychSession.SEQ_PRO
            )
            repository.updateSession(updated)
            settings.lastQuestionAt = System.currentTimeMillis()
            setPage(PsychPage.Work(situation, updated, questions, answers.size, answers, prompt = ok.prompt))
        }
    }

    private suspend fun complete(
        situation: PsychSituation,
        session: PsychSession,
        answers: List<PsychQa>
    ) {
        val updated = session.copy(
            status = PsychSession.STATUS_DONE,
            postponed = false,
            completedAt = System.currentTimeMillis()
        )
        repository.updateSession(updated)
        settings.activeSessionUid = ""
        activityLog?.end("psych-${session.sessionUid}", "${answers.size} отв.")
        activityLog?.instant(
            ru.na.step4.obidy.data.activity.ActivityCat.PSYCH,
            ru.na.step4.obidy.data.activity.ActivityType.FINISH,
            situation.text.take(80).ifBlank { "Психолог" },
            detail = "${answers.size} отв.",
            sessionKey = "psych-${session.sessionUid}"
        )
        setPage(PsychPage.Done(situation, updated, answers))
    }

    private fun workQuestionsFrom(ok: PsychAiClient.Result.Ok): List<String> {
        val listed = ok.questions.map { it.trim() }.filter { it.isNotEmpty() }
        if (listed.isNotEmpty()) return listed
        val decoded = PsychLogic.decodeQuestions(ok.text)
        if (decoded.isNotEmpty()) return decoded
        val single = ok.question.ifBlank { ok.text }.trim()
        if (single.isEmpty()) return emptyList()
        val starts = single.trimStart()
        if (starts.startsWith("[") || starts.startsWith("{")) return emptyList()
        return listOf(single)
    }

    private suspend fun refreshReminderText() {
        val result = withContext(Dispatchers.IO) {
            client.request(
                kind = "reminder_outreach",
                situation = "",
                answers = emptyList(),
                settings = settings,
                noHistory = false,
                topic = null
            )
        }
        val text = (result as? PsychAiClient.Result.Ok)?.text?.ifBlank { null }
            ?: PsychRu.reminderFallback
        settings.lastReminderText = text
        if (settings.inboxMessages().lastOrNull()?.text != text) {
            settings.appendInbox(text)
        }
    }

    private suspend fun callAi(
        kind: String,
        situation: PsychSituation,
        session: PsychSession,
        answers: List<PsychQa>,
        questionNumber: Int = 1,
        onOk: suspend (PsychAiClient.Result.Ok) -> Unit
    ) {
        val counts = kind in COUNTED
        val limit = if (isAdmin) 0 else settings.dailyLimit()
        if (counts && !isAdmin) {
            val used = repository.usageToday(settings.utcOffsetMinutes)
            if (limit > 0 && used >= limit) {
                _ui.value = _ui.value.copy(
                    waiting = false,
                    error = PsychRu.quotaGone.format(used, limit)
                )
                return
            }
        }
        val suffix = PsychLogic.dialogueSuffix(answers)
        val key = PsychLogic.cacheKey(situation.id, settings.languageCode, kind, suffix)
        if (!PsychLocks.tryLock(key)) {
            _ui.value = _ui.value.copy(error = PsychRu.busy)
            return
        }
        val waitKind = if (kind.contains("question")) "question" else "answer"
        startSpinner(waitKind)
        try {
            val cached = if (kind in SKIP_CACHE) null else repository.cached(key)
            val cacheUsable = cached != null &&
                !(kind.startsWith("question") && PsychLogic.decodeQuestions(cached.responseText).isEmpty())
            val ok = if (cacheUsable && cached != null) {
                PsychAiClient.Result.Ok(
                    text = cached.responseText,
                    speakable = cached.responseText,
                    question = cached.responseText,
                    questions = PsychLogic.decodeQuestions(cached.responseText),
                    prompt = cached.promptText
                )
            } else {
                val topics = if (!situation.noHistory) repository.topicsPayload(situation) else null
                val topic = topics?.optJSONObject(0)
                withContext(Dispatchers.IO) {
                    client.request(
                        kind = kind,
                        situation = situation.text,
                        answers = answers,
                        settings = settings,
                        noHistory = situation.noHistory,
                        topic = topic,
                        topics = topics,
                        questionNumber = questionNumber,
                        questionCount = if (kind.startsWith("question")) {
                            settings.workQuestionLimit
                        } else {
                            settings.dialogueExtraLimit
                        },
                        admin = isAdmin
                    )
                }
            }
            when (ok) {
                is PsychAiClient.Result.Err -> {
                    _ui.value = _ui.value.copy(waiting = false, error = ok.message)
                }
                    is PsychAiClient.Result.Ok -> {
                    if (!cacheUsable) repository.putCache(key, kind, ok.text, ok.prompt)
                    activityLog?.instant(
                        ru.na.step4.obidy.data.activity.ActivityCat.AI,
                        ru.na.step4.obidy.data.activity.ActivityType.AI,
                        kind,
                        detail = situation.text.take(80)
                    )
                    ok.personality?.let { if (settings.personalityCollectEnabled) settings.myPersonality = it }
                    var quotaLine: String? = null
                    var upsell: String? = null
                    if (counts) {
                        if (!isAdmin) repository.recordUsage(kind, situation.viaVoice)
                        val used = if (isAdmin) 0 else repository.usageToday(settings.utcOffsetMinutes)
                        quotaLine = if (limit <= 0) {
                            PsychRu.remainingUnlimited
                        } else {
                            PsychRu.quotaLeft.format((limit - used).coerceAtLeast(0), limit)
                        }
                        if (!isAdmin && !settings.isPro && PsychLogic.shouldUpsell(used, limit)) {
                            upsell = PsychRu.upsell
                        }
                    }
                    stopSpinner()
                    _ui.value = _ui.value.copy(
                        waiting = false,
                        quotaLine = quotaLine,
                        upsell = upsell,
                        error = null
                    )
                    onOk(ok)
                }
            }
        } finally {
            PsychLocks.unlock(key)
            stopSpinner()
        }
    }

    private fun displayText(kind: String, text: String): String = text

    private fun revealPendingFull() {
        val page = _ui.value.page
        val key = settings.pendingReadMoreKey
        val entry = PsychTeaserStore.get(key)
        if (page is PsychPage.Result && (entry != null || page.teaserKey.isNotBlank())) {
            val full = entry?.fullText ?: lastFullText
            val speak = entry?.speakable ?: page.speakable
            setPage(page.copy(text = full, speakable = speak, teaser = false))
        } else {
            goHub()
        }
    }

    private fun currentSituationSession(): Pair<PsychSituation, PsychSession>? {
        return when (val page = _ui.value.page) {
            is PsychPage.Dialogue -> page.situation to page.session
            is PsychPage.Result -> page.situation to page.session
            is PsychPage.Work -> page.situation to page.session
            is PsychPage.Done -> page.situation to page.session
            is PsychPage.Idle -> page.situation to page.session
            is PsychPage.Review -> page.situation to page.session
            else -> null
        }
    }

    private suspend fun maybeIdle() {
        val uid = settings.activeSessionUid
        if (uid.isBlank()) return
        val last = settings.lastQuestionAt
        if (last == 0L) return
        val idleMs = settings.liveIdleMinutes * 60_000L
        if (System.currentTimeMillis() - last < idleMs) return
        val session = repository.sessionByUid(uid) ?: return
        val situation = repository.getSituation(session.situationId) ?: return
        if (session.status == PsychSession.STATUS_DONE) return
        setPage(
            PsychPage.Idle(
                situation,
                session,
                work = session.sequentialWork != PsychSession.SEQ_LIVE
            )
        )
    }

    private suspend fun topicSnippets(): Map<Long, String> {
        return repository.allTopics().associate { topic ->
            topic.id to repository.lastStorySnippet(topic.id)
        }
    }

    private suspend fun refreshSnippets() {
        _ui.value = _ui.value.copy(topicSnippets = topicSnippets())
    }

    private suspend fun showTopicDetail(id: Long) {
        val topic = repository.getTopic(id) ?: return
        setPage(PsychPage.TopicDetail(topic, repository.topicStories(id)))
    }

    private fun setPage(page: PsychPage) {
        _ui.value = _ui.value.copy(page = page, error = null)
    }

    private fun bump() {
        _ui.value = _ui.value.copy()
    }

    private fun startSpinner(kind: String) {
        spinnerJob?.cancel()
        _ui.value = _ui.value.copy(waiting = true, waitKind = kind, spinner = 0)
        spinnerJob = viewModelScope.launch {
            var i = 0
            while (true) {
                delay(180)
                i = (i + 1) % 4
                _ui.value = _ui.value.copy(spinner = i)
            }
        }
    }

    private fun stopSpinner() {
        spinnerJob?.cancel()
        spinnerJob = null
        _ui.value = _ui.value.copy(waiting = false)
    }

    companion object {
        private val COUNTED = setOf(
            "analyze", "recommend", "questions", "questions_retry",
            "questions_next", "dialogue_question", "assistant"
        )
        private val SKIP_CACHE = setOf(
            "analyze", "recommend", "assistant", "questions", "questions_retry"
        )

        fun factory(
            repository: PsychRepository,
            settings: PsychSettings,
            spiritual: SpiritualRatingStore? = null,
            journalPrefs: ru.na.step4.obidy.data.journal.JournalPrefs? = null,
            activityLog: ru.na.step4.obidy.data.activity.ActivityLog? = null
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PsychViewModel(repository, settings, spiritual, journalPrefs, activityLog) as T
                }
            }
    }
}
