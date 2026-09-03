package ru.na.step4.obidy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.na.step4.obidy.data.CachedSituationAi
import ru.na.step4.obidy.data.InventoryAi
import ru.na.step4.obidy.data.InventoryAiCache
import ru.na.step4.obidy.data.InventoryFieldInsight
import ru.na.step4.obidy.data.InventoryStructure
import ru.na.step4.obidy.data.QuestionFocus
import ru.na.step4.obidy.data.ResentmentRepository
import ru.na.step4.obidy.data.Situation
import ru.na.step4.obidy.data.journal.EmotionCatalog
import ru.na.step4.obidy.data.journal.JournalAiClient
import ru.na.step4.obidy.data.journal.JournalPrefs
import ru.na.step4.obidy.data.journal.JournalPrompts
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.data.life.LifeBoardPrompts
import ru.na.step4.obidy.data.life.LifeBoardStore
import ru.na.step4.obidy.data.activity.ActivityLog

data class SituationEditUiState(
    val id: Long = 0,
    val resentmentId: Long = 0,
    val target: String = "",
    val title: String = "",
    val whatHappened: String = "",
    val iFelt: String = "",
    val iDid: String = "",
    val answers: Map<Int, String> = emptyMap(),
    val loaded: Boolean = false,
    val isPro: Boolean = false,
    val isAdmin: Boolean = false,
    val remainingAi: Int = JournalPrefs.DAILY_LIMIT,
    val aiLoading: Boolean = false,
    val aiNotice: String? = null,
    val aiPrompt: String = "",
    val insights: Map<String, InventoryFieldInsight> = emptyMap(),
    val fullAnalysis: String = ""
) {
    val progress: Int
        get() = toSituation().progressSteps

    val canUseAi: Boolean
        get() = true

    fun toSituation() = Situation(
        id = id,
        resentmentId = resentmentId,
        title = title,
        whatHappened = whatHappened,
        iFelt = iFelt,
        iDid = iDid
    ).let { base ->
        answers.entries.fold(base) { item, (number, value) -> item.withAnswer(number, value) }
    }

    fun valueOf(key: String): String = QuestionFocus.currentAnswer(toSituation(), key)

    fun emptyKeys(): List<String> =
        QuestionFocus.workKeys().filter { valueOf(it).isBlank() }

    fun filledKeys(): List<String> =
        QuestionFocus.workKeys().filter { valueOf(it).isNotBlank() }

    fun insightFor(key: String): InventoryFieldInsight? = insights[key]
}

class SituationEditViewModel(
    private val repository: ResentmentRepository,
    private val situationId: Long,
    private val prefs: JournalPrefs,
    private val lifeBoard: LifeBoardStore? = null,
    private val aiCache: InventoryAiCache,
    private val activityLog: ActivityLog? = null
) : ViewModel() {
    private val form = MutableStateFlow(SituationEditUiState(id = situationId, isPro = prefs.isPro, isAdmin = prefs.isAdmin))
    val uiState: StateFlow<SituationEditUiState> = form.asStateFlow()
    private var autosaveJob: Job? = null

    init {
        viewModelScope.launch {
            repository.getSituation(situationId)?.let { item ->
                val target = repository.getById(item.resentmentId)?.target.orEmpty()
                val cached = aiCache.get(item.id)
                form.value = item.toUiState(target).withCachedAi(cached)
                activityLog?.inventoryStart(
                    item.title.ifBlank { target },
                    item.id
                )
            }
        }
    }

    fun updateTitle(value: String) {
        form.update { it.copy(title = value) }
        scheduleAutosave()
    }

    fun updateWhatHappened(value: String) {
        form.update { it.copy(whatHappened = value) }
        scheduleAutosave()
    }

    fun updateIFelt(value: String) {
        form.update { it.copy(iFelt = value) }
        scheduleAutosave()
    }

    fun toggleFeltWord(word: String) {
        form.update { it.copy(iFelt = EmotionCatalog.toggleWord(it.iFelt, word)) }
        scheduleAutosave()
    }

    fun updateIDid(value: String) {
        form.update { it.copy(iDid = value) }
        scheduleAutosave()
    }

    fun updateAnswer(number: Int, value: String) {
        form.update { it.copy(answers = it.answers + (number to value)) }
        scheduleAutosave()
    }

    fun requestWorkThrough() {
        val snap = form.value
        if (snap.aiLoading) return
        val filledText = QuestionFocus.buildSituationAnswersText(snap.target, snap.toSituation())
        if (filledText.startsWith("(") || snap.toSituation().let {
                it.title.isBlank() && it.whatHappened.isBlank() && it.iFelt.isBlank() &&
                    it.iDid.isBlank() && (1..13).all { n -> it.answerFor(n).isBlank() }
            }
        ) {
            form.update {
                it.copy(
                    aiNotice = InventoryStructure.workThroughNeedText,
                    fullAnalysis = ""
                )
            }
            return
        }
        if (!prefs.canUseAi()) {
            form.update {
                it.copy(aiNotice = JournalRu.aiLimit)
            }
            return
        }
        val empty = snap.emptyKeys()
        val filled = snap.filledKeys()
        val fullMode = empty.isEmpty()
        viewModelScope.launch {
            autosaveJob?.cancel()
            repository.saveSituation(snap.toSituation().trimmed())
            form.update {
                it.copy(
                    aiLoading = true,
                    aiNotice = null,
                    aiPrompt = "",
                    fullAnalysis = if (fullMode) "" else it.fullAnalysis
                )
            }
            val aiKey = "ai-inventory-$situationId"
            activityLog?.aiBegin(
                if (fullMode) "Инвентарь · полный разбор" else "Инвентарь · подсказки",
                aiKey,
                snap.title.ifBlank { snap.target }
            )
            val target = snap.target.ifBlank {
                repository.getById(snap.resentmentId)?.target.orEmpty()
            }
            val types = repository.getTypesForSituation(snap.id).map { it.name }
            val program = prefs.profile.program.ifBlank {
                prefs.questionnaireAnswers()["section1:program_type"].orEmpty()
            }
            val personality = if (prefs.personalityEnabled) {
                prefs.personality.trim().ifBlank { null }
            } else {
                null
            }
            val questionnaire = LifeBoardPrompts.merge(
                JournalPrompts.formatQuestionnaire(prefs.profile),
                lifeBoard?.goalsPromptBlock()
            )
            val user = if (fullMode) {
                InventoryAi.fullAnalysisUserPrompt(
                    target = target,
                    typeNames = types,
                    situation = snap.toSituation(),
                    personality = personality,
                    questionnaire = questionnaire
                )
            } else {
                InventoryAi.workThroughUserPrompt(
                    target = target,
                    typeNames = types,
                    situation = snap.toSituation(),
                    emptyKeys = empty,
                    filledKeys = filled,
                    personality = personality,
                    questionnaire = questionnaire
                )
            }
            val result = withContext(Dispatchers.IO) {
                JournalAiClient.chat(
                    user = user,
                    role = if (fullMode) "inventory.analyze" else "inventory.work",
                    program = program,
                    premium = prefs.isPro || prefs.isAdmin,
                    admin = prefs.isAdmin
                )
            }
            activityLog?.aiDone(aiKey, snap.title.ifBlank { snap.target })
            form.update { current ->
                when (result) {
                    is JournalAiClient.Result.Ok -> {
                        prefs.consumeAi()
                        val remaining = if (prefs.isAdmin) Int.MAX_VALUE else prefs.remainingAiToday()
                        if (fullMode) {
                            val text = result.text.trim()
                            aiCache.save(current.id, current.insights, text)
                            current.copy(
                                aiLoading = false,
                                remainingAi = remaining,
                                aiPrompt = result.prompt,
                                fullAnalysis = text,
                                aiNotice = if (text.isBlank()) JournalRu.aiError else null
                            )
                        } else {
                            val list = InventoryAi.parseInsights(result.text, empty.toSet(), filled.toSet())
                            val newInsights = list.associateBy { it.key }
                            val cached = aiCache.mergeInsights(current.id, newInsights)
                            current.copy(
                                aiLoading = false,
                                remainingAi = remaining,
                                aiPrompt = result.prompt,
                                insights = cached.insights,
                                fullAnalysis = cached.fullAnalysis,
                                aiNotice = when {
                                    newInsights.isEmpty() -> result.text.ifBlank { JournalRu.aiError }
                                    else -> InventoryStructure.workThroughReadyHints
                                }
                            )
                        }
                    }
                    is JournalAiClient.Result.Err -> current.copy(
                        aiLoading = false,
                        aiPrompt = "",
                        aiNotice = result.message
                    )
                }
            }
        }
    }

    fun applyInsight(key: String) {
        val insight = form.value.insights[key] ?: return
        val text = insight.text.trim()
        if (text.isBlank()) return
        val current = form.value.valueOf(key)
        val next = if (current.isBlank()) text else "$current\n\n$text"
        setField(key, next)
    }

    fun dismissAnalysis() = form.update {
        it.copy(fullAnalysis = "", aiNotice = null, aiPrompt = "")
    }

    fun saveThen(onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            autosaveJob?.cancel()
            val id = repository.saveSituation(form.value.toSituation().trimmed())
            val savedId = if (id > 0) id else form.value.id
            if (savedId != form.value.id) form.update { it.copy(id = savedId) }
            onSaved(savedId)
        }
    }

    fun save(onSaved: () -> Unit) = saveThen { onSaved() }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            autosaveJob?.cancel()
            activityLog?.inventoryEnd(situationId, form.value.title)
            aiCache.clear(situationId)
            repository.getSituation(situationId)?.let { repository.deleteSituation(it) }
            onDeleted()
        }
    }

    override fun onCleared() {
        activityLog?.inventoryEnd(situationId, form.value.title)
        super.onCleared()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(450)
            val snap = form.value
            if (!snap.loaded || snap.id <= 0L) return@launch
            val id = repository.saveSituation(snap.toSituation().trimmed())
            if (id > 0 && id != snap.id) form.update { it.copy(id = id) }
        }
    }

    private fun setField(key: String, text: String) {
        val value = text.trim()
        if (value.isBlank()) return
        when (key) {
            QuestionFocus.TITLE -> updateTitle(value)
            QuestionFocus.WHAT -> updateWhatHappened(value)
            QuestionFocus.FELT -> updateIFelt(value)
            QuestionFocus.DID -> updateIDid(value)
            else -> {
                val n = key.removePrefix("q").toIntOrNull() ?: return
                updateAnswer(n, value)
            }
        }
    }

    private fun SituationEditUiState.withCachedAi(cached: CachedSituationAi?) = copy(
        insights = cached?.insights.orEmpty(),
        fullAnalysis = cached?.fullAnalysis.orEmpty()
    )

    private fun Situation.toUiState(target: String) = SituationEditUiState(
        id = id,
        resentmentId = resentmentId,
        target = target,
        title = title,
        whatHappened = whatHappened,
        iFelt = iFelt,
        iDid = iDid,
        answers = (1..13).associateWith(::answerFor),
        loaded = true,
        isPro = prefs.isPro,
        isAdmin = prefs.isAdmin,
        remainingAi = if (prefs.isAdmin) Int.MAX_VALUE else prefs.remainingAiToday()
    )

    private fun Situation.trimmed() = copy(
        title = title.trim(),
        whatHappened = whatHappened.trim(),
        iFelt = iFelt.trim(),
        iDid = iDid.trim()
    ).let { value ->
        (1..13).fold(value) { item, number -> item.withAnswer(number, item.answerFor(number).trim()) }
    }

    companion object {
        fun factory(
            repository: ResentmentRepository,
            id: Long,
            prefs: JournalPrefs,
            lifeBoard: LifeBoardStore? = null,
            aiCache: InventoryAiCache,
            activityLog: ActivityLog? = null
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SituationEditViewModel(repository, id, prefs, lifeBoard, aiCache, activityLog) as T
        }
    }
}
