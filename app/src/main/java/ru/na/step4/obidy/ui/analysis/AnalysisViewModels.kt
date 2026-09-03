package ru.na.step4.obidy.ui.analysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.Step4App
import ru.na.step4.obidy.data.analysis.AnalysisAnswers
import ru.na.step4.obidy.data.analysis.AnalysisAiCache
import ru.na.step4.obidy.data.analysis.AnalysisAiClient
import ru.na.step4.obidy.data.analysis.AnalysisCatalog
import ru.na.step4.obidy.data.analysis.AnalysisEngine
import ru.na.step4.obidy.data.analysis.AnalysisFlow
import ru.na.step4.obidy.data.analysis.AnalysisRecord
import ru.na.step4.obidy.data.analysis.AnalysisRepository
import ru.na.step4.obidy.data.analysis.AnalysisSettings
import ru.na.step4.obidy.data.analysis.CatalogEntry
import ru.na.step4.obidy.data.analysis.QaPair
import ru.na.step4.obidy.data.analysis.ReflectionQuestions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import ru.na.step4.obidy.data.analysis.AnalysisCatalogSync
import ru.na.step4.obidy.data.journal.JournalPrefs
import ru.na.step4.obidy.data.analysis.SessionScreen
import ru.na.step4.obidy.data.spiritual.SpiritualSource

class AnalysisSessionViewModel(
    app: Application,
    private val repository: AnalysisRepository,
    settings: AnalysisSettings,
    val catalogId: String
) : ViewModel() {
    private val progress = (app as Step4App).analysisProgress
    private val streakStore = (app as Step4App).analysisStreak
    private val spiritual = (app as Step4App).spiritualRating
    private val challenges = (app as Step4App).messengerChallenges
    private val engine: AnalysisEngine?
    private val _screen = MutableStateFlow<SessionScreen?>(null)
    val screen: StateFlow<SessionScreen?> = _screen.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val streakDays: StateFlow<Int> = streakStore.days

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _reviewIndex = MutableStateFlow<Int?>(null)
    val reviewIndex: StateFlow<Int?> = _reviewIndex.asStateFlow()

    private val _nav = MutableStateFlow(AnswerNav(canPrev = false, canNext = false, canResume = false))
    val answerNav: StateFlow<AnswerNav> = _nav.asStateFlow()

    private var savedOnce = false
    private var recordId = 0L
    private val extraAnswers = mutableListOf<QaPair>()
    private val saveMutex = Mutex()
    private var lastReflection: JSONObject? = null
    private var pendingReflection: JSONObject? = null

    val hasProgress: Boolean
        get() = engine?.hasProgress == true || extraAnswers.isNotEmpty() ||
            _draft.value.isNotBlank() || lastReflection != null || pendingReflection != null ||
            _reviewIndex.value != null

    val isDone: Boolean
        get() = engine?.isDone == true && _reviewIndex.value == null

    val isReviewing: Boolean
        get() = _reviewIndex.value != null

    init {
        val entry = AnalysisCatalog.byId(app, catalogId, settings)
        engine = entry?.let { AnalysisEngine(it) }
        val snap = progress.session(catalogId)
        if (engine != null && snap != null) {
            val engineObj = snap.optJSONObject("engine")
            val restored = engineObj != null && engine.restore(engineObj)
            if (restored) {
                extraAnswers += AnalysisAnswers.decode(
                    snap.optJSONArray("extra")?.toString().orEmpty()
                )
                _draft.value = snap.optString("draft")
                savedOnce = snap.optBoolean("saved_once")
                recordId = snap.optLong("record_id")
                _saved.value = savedOnce && recordId > 0L
                val reflection = snap.optJSONObject("reflection")
                lastReflection = reflection
                pendingReflection = reflection
            } else {
                engine.restart()
                progress.clear(catalogId)
            }
        }
        _screen.value = engine?.screen()
        publish()
        refreshNav()
        if (engine?.isDone == true && savedOnce && recordId == 0L) {
            viewModelScope.launch { persist() }
        }
    }

    fun begin() {
        engine?.begin()
        publish()
        persistProgress(markActive = true)
    }

    fun pickMiniCount(count: Int) {
        engine?.pickMiniCount(count)
        publish()
        persistProgress(markActive = true)
    }

    fun reroll() {
        engine?.reroll()
        publish()
        persistProgress(markActive = true)
    }

    fun submit(text: String) {
        val review = _reviewIndex.value
        if (review != null) {
            if (engine?.replaceAnswerText(review, text) != true) return
            _reviewIndex.value = null
            savedOnce = false
            _saved.value = false
            afterMove()
            return
        }
        engine?.submit(text)
        afterMove()
    }

    fun choose(id: String, extraText: String = "") {
        val review = _reviewIndex.value
        if (review != null) {
            if (engine?.replaceAnswerChoice(review, id, extraText) != true) return
            _reviewIndex.value = null
            savedOnce = false
            _saved.value = false
            afterMove()
            return
        }
        engine?.choose(id, extraText)
        afterMove()
    }

    fun setDraft(text: String) {
        _draft.value = text
    }

    fun goPrevAnswer() {
        val e = engine ?: return
        val n = e.answerCount
        if (n == 0) return
        val cur = _reviewIndex.value
        val target = when {
            cur == null -> n - 1
            cur > 0 -> cur - 1
            else -> return
        }
        openReview(target)
    }

    fun goNextAnswer() {
        val e = engine ?: return
        val cur = _reviewIndex.value ?: return
        if (cur < e.answerCount - 1) {
            openReview(cur + 1)
        } else {
            resumeLive()
        }
    }

    fun resumeLive() {
        if (_reviewIndex.value == null) return
        _reviewIndex.value = null
        _draft.value = ""
        publish()
        refreshNav()
        persistProgress(markActive = true)
    }

    private fun openReview(index: Int) {
        val e = engine ?: return
        val pair = e.answerAt(index) ?: return
        val peek = e.peekQuestionScreen(index)
        _reviewIndex.value = index
        _draft.value = pair.answer
        _screen.value = (peek ?: SessionScreen.Question(
            title = e.title,
            prayer = null,
            question = pair.question,
            choices = emptyList(),
            allowText = true,
            hideSend = false,
            progressIndex = index + 1,
            progressTotal = (peek?.progressTotal ?: e.answerCount).coerceAtLeast(index + 1)
        )).let { q ->
            q.copy(
                progressIndex = index + 1,
                allowText = true,
                hideSend = false
            )
        }
        refreshNav()
    }

    fun restart() {
        savedOnce = false
        recordId = 0L
        extraAnswers.clear()
        _saved.value = false
        _draft.value = ""
        _reviewIndex.value = null
        lastReflection = null
        pendingReflection = null
        engine?.restart()
        progress.clear(catalogId)
        publish()
        refreshNav()
    }

    fun appendReflection(pairs: List<QaPair>) {
        if (pairs.isEmpty()) return
        extraAnswers += pairs
        publish()
        viewModelScope.launch { persist() }
    }

    fun consumeReflection(): JSONObject? {
        val snap = pendingReflection
        pendingReflection = null
        return snap
    }

    fun persistProgress(
        markActive: Boolean = true,
        reflection: JSONObject? = lastReflection,
        leaving: Boolean = false
    ) {
        lastReflection = reflection
        val e = engine ?: return
        val questions = reflection?.optJSONArray("questions")
        val reflectionActive = questions != null &&
            questions.length() > 0 &&
            reflection.optInt("index") < questions.length()
        if (leaving && e.isDone && !reflectionActive) {
            progress.clear(catalogId)
            if (!markActive) progress.clearLastActive()
            return
        }
        if (!shouldKeep(reflectionActive)) {
            if (!markActive) progress.clearLastActive()
            return
        }
        val session = JSONObject()
            .put("engine", e.capture())
            .put("extra", JSONArray(AnalysisAnswers.encode(extraAnswers)))
            .put("draft", _draft.value)
            .put("saved_once", savedOnce)
            .put("record_id", recordId)
        if (reflectionActive) {
            session.put("reflection", reflection)
        }
        progress.save(catalogId, session, markActive)
    }

    private fun shouldKeep(reflectionActive: Boolean): Boolean {
        if (engine == null) return false
        if (engine.isDone) return true
        if (hasProgress || reflectionActive || _draft.value.isNotBlank()) return true
        val s = engine.screen()
        return s is SessionScreen.Question ||
            (s is SessionScreen.Preview && s.selectedCount != null)
    }

    private fun afterMove() {
        _draft.value = ""
        _reviewIndex.value = null
        publish()
        refreshNav()
        val next = engine?.screen()
        if (next is SessionScreen.Done && !savedOnce) {
            savedOnce = true
            viewModelScope.launch { persist() }
        }
        persistProgress(markActive = true)
    }

    private suspend fun persist() {
        val done = engine?.screen() as? SessionScreen.Done ?: return
        val all = done.answers + extraAnswers
        var firstSave = false
        saveMutex.withLock {
            if (recordId == 0L) {
                recordId = repository.save(catalogId, done.title, all)
                firstSave = true
            } else {
                repository.replaceAnswers(recordId, all)
            }
        }
        if (firstSave) {
            streakStore.recordCompletion()
            spiritual.applyTask(SpiritualSource.ANALYSIS)
            val title = done.title
            viewModelScope.launch {
                challenges.shareAnalysis(title)
            }
        }
        _saved.value = true
        persistProgress(markActive = true)
    }

    private fun publish() {
        if (_reviewIndex.value != null) {
            refreshNav()
            return
        }
        val s = engine?.screen()
        _screen.value = if (s is SessionScreen.Done && extraAnswers.isNotEmpty()) {
            s.copy(answers = s.answers + extraAnswers)
        } else {
            s
        }
        refreshNav()
    }

    private fun refreshNav() {
        val e = engine
        val n = e?.answerCount ?: 0
        val review = _reviewIndex.value
        val ok = e?.canReviewAnswers == true
        _nav.value = AnswerNav(
            canPrev = ok && n > 0 && (review == null || review > 0),
            canNext = ok && review != null && review < n - 1,
            canResume = ok && review != null
        )
    }

    companion object {
        fun factory(
            app: Application,
            repository: AnalysisRepository,
            settings: AnalysisSettings,
            catalogId: String
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AnalysisSessionViewModel(app, repository, settings, catalogId) as T
            }
        }
    }
}

data class AnswerNav(
    val canPrev: Boolean,
    val canNext: Boolean,
    val canResume: Boolean
)

class AnalysisHistoryViewModel(
    private val repository: AnalysisRepository
) : ViewModel() {
    val records: StateFlow<List<AnalysisRecord>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        fun factory(repository: AnalysisRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AnalysisHistoryViewModel(repository) as T
            }
        }
    }
}

sealed class AiReviewUi {
    data object Idle : AiReviewUi()
    data object Loading : AiReviewUi()
    data class Ready(
        val text: String,
        val fromCache: Boolean = false,
        val prompt: String = ""
    ) : AiReviewUi()
    data class Error(val message: String) : AiReviewUi()
}

class AnalysisAiReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val profile = (application as Step4App).profileStore
    private val spiritual = (application as Step4App).spiritualRating
    private val cache = AnalysisAiCache(application)
    private val _state = MutableStateFlow<AiReviewUi>(AiReviewUi.Idle)
    val state: StateFlow<AiReviewUi> = _state.asStateFlow()

    private fun premiumActive(): Boolean {
        val app = getApplication<Step4App>()
        return app.journalPrefs.isPro || app.journalPrefs.isAdmin || app.psychSettings.isPro
    }

    fun showCached(title: String, answers: List<QaPair>) {
        if (_state.value !is AiReviewUi.Idle) return
        val cached = cache.get(title, answers) ?: return
        _state.value = AiReviewUi.Ready(
            ReflectionQuestions.cleanReviewSource(cached),
            fromCache = true
        )
    }

    fun request(title: String, answers: List<QaPair>, force: Boolean = false) {
        if (_state.value is AiReviewUi.Loading) return
        if (!force) {
            val cached = cache.get(title, answers)
            if (!cached.isNullOrBlank()) {
                _state.value = AiReviewUi.Ready(
                    ReflectionQuestions.cleanReviewSource(cached),
                    fromCache = true
                )
                return
            }
        }
        viewModelScope.launch {
            _state.value = AiReviewUi.Loading
            val result = withContext(Dispatchers.IO) {
                AnalysisAiClient.analyze(
                    title,
                    answers,
                    profile,
                    premium = premiumActive(),
                    admin = getApplication<Step4App>().journalPrefs.isAdmin,
                    goals = getApplication<Step4App>().lifeBoard.goalsPromptBlock()
                )
            }
            _state.value = when (result) {
                is AnalysisAiClient.Result.Ok -> {
                    val eventId = "analysis-" + (title.hashCode().toString() + "-" + answers.size)
                    val parsed = ru.na.step4.obidy.data.profile.PersonalityPortrait.parse(result.text)
                    val visible = spiritual.consumeAiText(eventId, parsed.first)
                    val portrait = result.personality ?: parsed.second
                    if (profile.personalityCollectEnabled && !portrait.isNullOrBlank()) {
                        profile.personality = portrait
                    }
                    cache.put(title, answers, visible)
                    AiReviewUi.Ready(visible, fromCache = false, prompt = result.prompt)
                }
                is AnalysisAiClient.Result.Err -> AiReviewUi.Error(result.message)
            }
        }
    }

    fun reset() {
        _state.value = AiReviewUi.Idle
    }
}

class AnalysisReflectionViewModel : ViewModel() {
    private var questions: List<String> = emptyList()
    private var index = 0
    private val _question = MutableStateFlow<SessionScreen.Question?>(null)
    val question: StateFlow<SessionScreen.Question?> = _question.asStateFlow()

    val inProgress: Boolean
        get() = _question.value != null

    fun start(items: List<String>) {
        if (items.isEmpty() || inProgress) return
        questions = items
        index = 0
        publishQuestion()
    }

    fun snapshot(): JSONObject? {
        if (!inProgress) return null
        return JSONObject()
            .put("index", index)
            .put("questions", JSONArray().also { arr ->
                questions.forEach { arr.put(it) }
            })
    }

    fun restore(obj: JSONObject) {
        val arr = obj.optJSONArray("questions") ?: return
        val items = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        val i = obj.optInt("index")
        if (items.isEmpty() || i !in items.indices) {
            reset()
            return
        }
        questions = items
        index = i
        publishQuestion()
    }

    fun submit(text: String): QaPair? {
        val answer = text.trim()
        val current = _question.value ?: return null
        if (answer.isEmpty()) return null
        val pair = QaPair(current.question, answer)
        index++
        if (index >= questions.size) {
            _question.value = null
            questions = emptyList()
            index = 0
        } else {
            publishQuestion()
        }
        return pair
    }

    fun reset() {
        questions = emptyList()
        index = 0
        _question.value = null
    }

    private fun publishQuestion() {
        _question.value = SessionScreen.Question(
            title = Ru.analysisReflection,
            prayer = null,
            question = questions[index],
            choices = emptyList(),
            allowText = true,
            hideSend = false,
            progressIndex = index + 1,
            progressTotal = questions.size
        )
    }
}

class AnalysisEditorViewModel(
    app: Application,
    private val settings: AnalysisSettings,
    private val prefs: JournalPrefs,
    val catalogId: String
) : AndroidViewModel(app) {
    private val _entry = MutableStateFlow(loadEntry())
    val entry: StateFlow<CatalogEntry> = _entry.asStateFlow()
    private val _publish = MutableStateFlow<AnalysisPublishUi>(AnalysisPublishUi.Idle)
    val publish: StateFlow<AnalysisPublishUi> = _publish.asStateFlow()
    private var pushJob: Job? = null

    val isAdmin: Boolean
        get() = prefs.isAdmin

    val overridden: Boolean
        get() = !entry.value.custom && settings.isOverridden(catalogId)

    fun update(transform: (CatalogEntry) -> CatalogEntry) {
        val next = transform(_entry.value)
        val filled = when {
            next.flow == AnalysisFlow.CLEAN_DAY -> next
            next.questions.isEmpty() -> next.copy(questions = listOf(AnalysisCatalog.blankQuestion()))
            else -> next
        }
        _entry.value = filled
        if (isAdmin) {
            settings.applyStandardEntry(filled, AnalysisCatalog.loadDefaults(getApplication()))
            schedulePush()
            if (_publish.value is AnalysisPublishUi.Ok || _publish.value is AnalysisPublishUi.Error) {
                _publish.value = AnalysisPublishUi.Idle
            }
        } else {
            settings.saveOverride(filled)
        }
    }

    fun reset() {
        if (entry.value.custom) return
        val original = AnalysisCatalog.byId(getApplication(), catalogId) ?: return
        if (isAdmin) {
            settings.applyStandardEntry(original, AnalysisCatalog.loadDefaults(getApplication()))
            schedulePush()
        } else {
            settings.clearOverride(catalogId)
        }
        _entry.value = AnalysisCatalog.byId(getApplication(), catalogId, settings) ?: original
    }

    fun deleteCustom() {
        if (!entry.value.custom) return
        if (isAdmin) {
            settings.removeStandardEntry(catalogId, AnalysisCatalog.loadDefaults(getApplication()))
            viewModelScope.launch { AnalysisCatalogSync.push(settings, prefs) }
        } else {
            settings.deleteCustom(catalogId)
        }
    }

    /** Publish current catalog (including this analysis) to the shared server catalog. */
    fun publishShared() {
        if (!isAdmin) return
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            _publish.value = AnalysisPublishUi.Busy
            val filled = _entry.value.let { next ->
                when {
                    next.flow == AnalysisFlow.CLEAN_DAY -> next
                    next.questions.isEmpty() -> next.copy(questions = listOf(AnalysisCatalog.blankQuestion()))
                    else -> next
                }
            }
            _entry.value = filled
            settings.applyStandardEntry(filled, AnalysisCatalog.loadDefaults(getApplication()))
            val ok = AnalysisCatalogSync.push(settings, prefs)
            _publish.value = if (ok) AnalysisPublishUi.Ok else AnalysisPublishUi.Error
        }
    }

    private fun schedulePush() {
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            delay(900)
            val ok = AnalysisCatalogSync.push(settings, prefs)
            if (!ok && settings.catalogDirty) {
                _publish.value = AnalysisPublishUi.Error
            }
        }
    }

    private fun loadEntry(): CatalogEntry {
        return AnalysisCatalog.byId(getApplication(), catalogId, settings)
            ?: AnalysisCatalog.blankCustom(Ru.analysisCreate, 200)
    }

    companion object {
        fun factory(
            app: Application,
            settings: AnalysisSettings,
            prefs: JournalPrefs,
            catalogId: String
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AnalysisEditorViewModel(app, settings, prefs, catalogId) as T
            }
        }
    }
}

sealed class AnalysisPublishUi {
    data object Idle : AnalysisPublishUi()
    data object Busy : AnalysisPublishUi()
    data object Ok : AnalysisPublishUi()
    data object Error : AnalysisPublishUi()
}
