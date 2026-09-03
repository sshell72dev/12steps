package ru.na.step4.obidy.ui.journal

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.na.step4.obidy.data.journal.CachedEntryAnalyze
import ru.na.step4.obidy.data.journal.EmotionCatalog
import ru.na.step4.obidy.data.journal.JournalAiClient
import ru.na.step4.obidy.data.journal.JournalEntry
import ru.na.step4.obidy.data.journal.JournalFieldKind
import ru.na.step4.obidy.data.journal.JournalFieldSpec
import ru.na.step4.obidy.data.journal.JournalFields
import ru.na.step4.obidy.data.journal.JournalPrefs
import ru.na.step4.obidy.data.journal.JournalPrompts
import ru.na.step4.obidy.data.journal.JournalRu
import ru.na.step4.obidy.data.journal.JournalStore
import ru.na.step4.obidy.data.life.LifeBoardPrompts
import ru.na.step4.obidy.data.journal.NodeType
import ru.na.step4.obidy.data.journal.TreeCatalog
import ru.na.step4.obidy.data.journal.TreeNode
import ru.na.step4.obidy.data.journal.TreePath
import ru.na.step4.obidy.data.profile.QuestionnaireQuestion
import ru.na.step4.obidy.data.spiritual.SpiritualSource
import ru.na.step4.obidy.Step4App

data class JournalState(
    val registered: Boolean = false,
    val name: String = "",
    val problems: Set<String> = emptySet(),
    val path: TreePath? = null,
    val entries: List<JournalEntry> = emptyList(),
    val personality: String = "",
    val personalityEnabled: Boolean = true,
    val personalityCollectEnabled: Boolean = false,
    val isPro: Boolean = false,
    val isAdmin: Boolean = false,
    val remainingAi: Int = JournalPrefs.DAILY_LIMIT,
    val draft: String = "",
    val splitFields: Boolean = true,
    val fields: List<JournalFieldSpec> = JournalFields.defaults,
    val fieldValues: Map<String, String> = emptyMap(),
    val lastSaved: JournalEntry? = null,
    val editingId: String? = null,
    val notice: String? = null
) {
    val currentCount: Int
        get() {
            val id = path?.current?.id ?: return 0
            return entries.count { it.nodeId == id }
        }

    val writable: Boolean
        get() = path?.current?.canWrite == true
}

sealed class AiUi {
    data object Idle : AiUi()
    data class NeedQuestion(val question: QuestionnaireQuestion) : AiUi()
    data object Loading : AiUi()
    data class Ready(
        val text: String,
        val portrait: String?,
        val fromCache: Boolean,
        val prompt: String = ""
    ) : AiUi()
    data class Error(val message: String) : AiUi()
}

class JournalViewModel(
    private val app: Application,
    private val store: JournalStore,
    private val prefs: JournalPrefs
) : ViewModel() {
    val catalog: TreeCatalog.Catalog = TreeCatalog.load(app)
    val streakDays: StateFlow<Int> = (app as Step4App).journalStreak.days

    fun streakLabel(days: Int = streakDays.value): String? =
        (app as Step4App).journalStreak.label(days)

    private val _meta = MutableStateFlow(readMeta())
    val state: StateFlow<JournalState> = combine(store.entries, _meta, prefs.profile.snapshot) { entries, meta, profile ->
        meta.copy(
            entries = entries,
            name = profile.name,
            problems = profile.problems,
            personality = profile.personality,
            personalityEnabled = profile.personalityEnabled,
            personalityCollectEnabled = profile.personalityCollectEnabled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _meta.value)

    private val _ai = MutableStateFlow<AiUi>(AiUi.Idle)
    val ai: StateFlow<AiUi> = _ai.asStateFlow()

    private val _expandedStep = MutableStateFlow<Int?>(null)
    val expandedStep: StateFlow<Int?> = _expandedStep.asStateFlow()

    private val _expandedChapter = MutableStateFlow<Int?>(null)
    val expandedChapter: StateFlow<Int?> = _expandedChapter.asStateFlow()

    /** User tapped a step header — show every chapter in that step. */
    private val _pickAllChapters = MutableStateFlow(false)
    val pickAllChapters: StateFlow<Boolean> = _pickAllChapters.asStateFlow()

    private val _chapterShowsAllPoints = MutableStateFlow<Set<Int>>(emptySet())
    val chapterShowsAllPoints: StateFlow<Set<Int>> = _chapterShowsAllPoints.asStateFlow()

    private var skipQuestOnce = false

    fun nextPoint(): TreeNode? {
        val id = state.value.path?.current?.id ?: return null
        return catalog.nextPoint(id)
    }

    fun toggleStep(id: Int) {
        _expandedStep.value = if (_expandedStep.value == id) null else id
        _expandedChapter.value = null
    }

    fun toggleChapter(id: Int) {
        _expandedChapter.value = if (_expandedChapter.value == id) null else id
    }

    fun syncPickExpansion() {
        val path = state.value.path
        if (path == null) {
            _expandedStep.value = null
            _expandedChapter.value = null
            _pickAllChapters.value = false
            _chapterShowsAllPoints.value = emptySet()
            return
        }
        _expandedStep.value = path.step.id
        _expandedChapter.value = path.chapter?.id
        _pickAllChapters.value = false
        _chapterShowsAllPoints.value = emptySet()
    }

    fun togglePickStep(id: Int) {
        when {
            _expandedStep.value != id -> {
                _expandedStep.value = id
                _pickAllChapters.value = true
                _expandedChapter.value = null
                _chapterShowsAllPoints.value = emptySet()
            }
            _pickAllChapters.value -> {
                _expandedStep.value = null
                _pickAllChapters.value = false
                _expandedChapter.value = null
                _chapterShowsAllPoints.value = emptySet()
            }
            else -> {
                _pickAllChapters.value = true
                _expandedChapter.value = null
                _chapterShowsAllPoints.value = emptySet()
            }
        }
    }

    fun togglePickChapter(id: Int) {
        if (_pickAllChapters.value) {
            _chapterShowsAllPoints.update { if (id in it) it - id else it + id }
            return
        }
        val currentChapterId = state.value.path?.chapter?.id
        if (_expandedChapter.value == id) {
            if (id == currentChapterId && id !in _chapterShowsAllPoints.value) {
                _chapterShowsAllPoints.update { it + id }
            } else {
                _expandedChapter.value = null
                _chapterShowsAllPoints.update { it - id }
            }
        } else {
            _expandedChapter.value = id
            _chapterShowsAllPoints.update { it + id }
        }
    }

    fun visiblePickPoints(chapter: TreeNode): List<TreeNode> {
        if (_pickAllChapters.value && _expandedStep.value == chapter.parentId) {
            return chapter.children
        }
        val path = state.value.path ?: return chapter.children
        if (chapter.id != path.chapter?.id) return chapter.children
        val point = path.point ?: return chapter.children
        return if (chapter.id in _chapterShowsAllPoints.value) chapter.children else listOf(point)
    }

    fun selectPickNode(id: Int, onLeaf: () -> Unit) {
        val node = catalog.node(id) ?: return
        if (!node.isLeaf) return
        setCurrentPlace(id)
        syncPickExpansion()
        onLeaf()
    }

    fun selectNode(id: Int) {
        val node = catalog.node(id) ?: return
        if (!node.isLeaf) {
            when (node.type) {
                NodeType.STEP -> toggleStep(id)
                NodeType.CHAPTER -> toggleChapter(id)
                NodeType.POINT -> Unit
            }
            return
        }
        setCurrentPlace(id)
    }

    fun selectResentmentPlace() {
        val chapter = catalog.resentmentChapter() ?: return
        setCurrentPlace(chapter.id, clearDraft = false)
        _expandedStep.value = chapter.parentId
        _expandedChapter.value = chapter.id
    }

    private fun setCurrentPlace(id: Int, clearDraft: Boolean = true) {
        prefs.currentId = id
        if (clearDraft) {
            persistDraft("", null)
            prefs.fieldValues = emptyMap()
            _meta.update { readMeta().copy(lastSaved = null) }
        } else {
            _meta.update { old ->
                readMeta().copy(
                    draft = old.draft,
                    fieldValues = old.fieldValues,
                    lastSaved = old.lastSaved,
                    editingId = old.editingId,
                    notice = old.notice
                )
            }
        }
    }

    fun goNextPoint() {
        val next = nextPoint() ?: return
        selectNode(next.id)
        val chapter = catalog.pathOf(next.id)?.chapter
        _expandedStep.value = catalog.pathOf(next.id)?.step?.id
        _expandedChapter.value = chapter?.id
    }

    fun setDraft(text: String) {
        persistDraft(text, _meta.value.editingId)
        _meta.update { it.copy(draft = text) }
        if (text.isNotBlank()) markJournalWriting()
    }

    fun setSplitFields(split: Boolean) {
        if (split == _meta.value.splitFields) return
        if (split) {
            val parsed = JournalFields.parse(_meta.value.draft, _meta.value.fields)
            if (parsed.isNotEmpty()) {
                prefs.fieldValues = parsed
                _meta.update { it.copy(splitFields = true, fieldValues = parsed) }
            } else {
                _meta.update { it.copy(splitFields = true) }
            }
        } else {
            val composed = JournalFields.format(_meta.value.fields, _meta.value.fieldValues)
            if (composed.isNotBlank()) {
                persistDraft(composed, _meta.value.editingId)
                _meta.update { it.copy(splitFields = false, draft = composed) }
            } else {
                _meta.update { it.copy(splitFields = false) }
            }
        }
        prefs.splitFields = split
    }

    fun setFieldValue(id: String, text: String) {
        val next = _meta.value.fieldValues.toMutableMap()
        if (text.isBlank()) next.remove(id) else next[id] = text
        prefs.fieldValues = next
        _meta.update { it.copy(fieldValues = next) }
        if (text.isNotBlank()) markJournalWriting()
    }

    fun toggleFieldWord(fieldId: String, word: String) {
        val current = _meta.value.fieldValues[fieldId].orEmpty()
        val kind = _meta.value.fields.find { it.id == fieldId }?.kind ?: JournalFieldKind.FEELINGS
        setFieldValue(fieldId, EmotionCatalog.toggleWord(current, word, kind))
    }

    fun addField(title: String, kind: JournalFieldKind) {
        val field = JournalFields.newField(title, kind)
        val next = _meta.value.fields + field
        prefs.fields = next
        _meta.update { it.copy(fields = next) }
    }

    fun renameField(id: String, title: String) {
        val trimmed = title.trim().ifBlank { return }
        val next = _meta.value.fields.map { field ->
            if (field.id != id) field
            else field.copy(title = trimmed, kind = JournalFields.kindFromTitle(trimmed, field.kind))
        }
        prefs.fields = next
        _meta.update { it.copy(fields = next) }
    }

    fun removeField(id: String) {
        val current = _meta.value.fields
        if (current.size <= 1) return
        val next = current.filterNot { it.id == id }
        val values = _meta.value.fieldValues - id
        prefs.fields = next
        prefs.fieldValues = values
        _meta.update { it.copy(fields = next, fieldValues = values) }
    }

    fun moveField(id: String, delta: Int) {
        val current = _meta.value.fields.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        val target = index + delta
        if (index < 0 || target !in current.indices) return
        val item = current.removeAt(index)
        current.add(target, item)
        prefs.fields = current
        _meta.update { it.copy(fields = current) }
    }

    fun clearNotice() {
        _meta.update { it.copy(notice = null) }
    }

    fun saveDraft() {
        val snap = state.value
        val node = snap.path?.current
        val text = if (snap.splitFields) {
            JournalFields.format(snap.fields, snap.fieldValues).trim()
        } else {
            snap.draft.trim()
        }
        if (node == null || !node.canWrite) {
            _meta.update { it.copy(notice = JournalRu.needPlace) }
            return
        }
        if (text.isBlank()) {
            _meta.update { it.copy(notice = JournalRu.emptyDraft) }
            return
        }
        viewModelScope.launch {
            val saved = if (snap.editingId != null) {
                store.update(snap.editingId, text)
            } else {
                val pointTitle = node.displayTitle()
                store.add(node.id, text).also {
                    val app = app as Step4App
                    app.spiritualRating.applyTask(SpiritualSource.JOURNAL)
                    app.journalStreak.recordCompletion()
                    launch { app.messengerChallenges.shareJournal(pointTitle) }
                }
            }
            persistDraft("", null)
            prefs.fieldValues = emptyMap()
            (app as Step4App).activityLog.journalWriteEnd(text.take(80))
            (app as Step4App).activityLog.instant(
                ru.na.step4.obidy.data.activity.ActivityCat.JOURNAL,
                ru.na.step4.obidy.data.activity.ActivityType.SAVE,
                node.displayTitle(),
                detail = text.take(80)
            )
            _meta.update {
                it.copy(
                    draft = "",
                    fieldValues = emptyMap(),
                    editingId = null,
                    lastSaved = saved,
                    notice = JournalRu.saved
                )
            }
        }
    }

    fun startEdit(entry: JournalEntry) {
        persistDraft(entry.text, entry.id)
        val parsed = JournalFields.parse(entry.text, _meta.value.fields)
        val split = _meta.value.splitFields
        if (split) {
            prefs.fieldValues = parsed
            _meta.update {
                it.copy(
                    draft = entry.text,
                    fieldValues = parsed,
                    editingId = entry.id,
                    lastSaved = entry
                )
            }
        } else {
            _meta.update { it.copy(draft = entry.text, editingId = entry.id, lastSaved = entry) }
        }
    }

    fun cancelEdit() {
        persistDraft("", null)
        prefs.fieldValues = emptyMap()
        _meta.update { it.copy(draft = "", fieldValues = emptyMap(), editingId = null) }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            store.delete(id)
            (app as Step4App).journalAnalyzeCache.clear(id)
        }
    }

    fun register(name: String, problems: Set<String>) {
        prefs.name = name
        prefs.problems = problems
        prefs.registered = true
        refreshMeta()
    }

    fun setName(value: String) {
        prefs.name = value
        refreshMeta()
    }

    fun toggleProblem(key: String) {
        val next = prefs.problems.toMutableSet()
        if (!next.add(key)) next.remove(key)
        prefs.problems = next
        refreshMeta()
    }

    fun setPro(value: Boolean) {
        prefs.isPro = value
        syncPsychPremium(value)
        refreshMeta()
    }

    fun setUserRole() {
        prefs.isPro = false
        syncPsychPremium(false)
        notesRepo()?.clearAdmin()
        refreshMeta()
    }

    fun setProRole() {
        prefs.isPro = true
        syncPsychPremium(true)
        notesRepo()?.clearAdmin()
        refreshMeta()
    }

    fun activateAdmin(code: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = notesRepo()?.activateAdmin(code) == true
            _meta.update { it.copy(notice = if (ok) JournalRu.adminConnected else JournalRu.adminCodeBad) }
            refreshMeta()
            onResult(ok)
        }
    }

    private fun notesRepo() = (app as ru.na.step4.obidy.Step4App).notesRepository

    fun setPersonalityEnabled(value: Boolean) {
        prefs.personalityEnabled = value
        refreshMeta()
    }

    fun setPersonality(text: String) {
        prefs.personality = text.trim()
        refreshMeta()
    }

    fun pendingQuestion(): QuestionnaireQuestion? = prefs.profile.nextUnanswered()

    fun answerQuestion(id: String, value: String) {
        prefs.putQuestionnaireAnswer(id, value)
    }

    fun skipQuestion(id: String) {
        prefs.skipQuestion(id)
    }

    fun prepareAiHelp(): Boolean {
        if (skipQuestOnce) {
            skipQuestOnce = false
            return true
        }
        val question = pendingQuestion()
        if (question != null) {
            _ai.value = AiUi.NeedQuestion(question)
            return false
        }
        return true
    }

    fun continueWithoutQuestionnaire() {
        skipQuestOnce = true
    }

    fun requestHelp(entryId: String? = null) {
        if (entryId != null) {
            requestHelpOnEntry(entryId)
            return
        }
        val path = state.value.path ?: return
        val cached = prefs.cachedHelp(path.current.id)
        if (!cached.isNullOrBlank()) {
            _ai.value = AiUi.Ready(cached, null, fromCache = true)
            return
        }
        if (!prefs.canUseAi()) {
            _ai.value = AiUi.Error(JournalRu.aiLimit)
            return
        }
        viewModelScope.launch {
            _ai.value = AiUi.Loading
            val log = (app as Step4App).activityLog
            val key = "ai-journal-help-${path.current.id}"
            log.aiBegin("Дневник · помощь", key, path.current.displayTitle())
            val program = currentProgram()
            val personality = personalityForPrompt()
            val questionnaire = LifeBoardPrompts.merge(
                JournalPrompts.formatQuestionnaire(prefs.profile),
                (app as Step4App).lifeBoard.goalsPromptBlock()
            )
            val user = JournalPrompts.helpPointUser(
                path = path,
                personality = personality,
                questionnaire = questionnaire
            )
            val result = withContext(Dispatchers.IO) {
                JournalAiClient.chat(
                    user = user,
                    role = "journal.help",
                    program = program,
                    premium = premiumActive(),
                    admin = prefs.isAdmin
                )
            }
            log.aiDone(key, path.current.displayTitle())
            _ai.value = when (result) {
                is JournalAiClient.Result.Ok -> {
                    prefs.consumeAi()
                    prefs.putCachedHelp(path.current.id, result.text)
                    refreshMeta()
                    AiUi.Ready(result.text, null, fromCache = false, prompt = result.prompt)
                }
                is JournalAiClient.Result.Err -> AiUi.Error(result.message)
            }
        }
    }

    private fun requestHelpOnEntry(entryId: String) {
        if (!prefs.canUseAi()) {
            _ai.value = AiUi.Error(JournalRu.aiLimit)
            return
        }
        val entry = store.byId(entryId)
        val path = entry?.let { catalog.pathOf(it.nodeId) }
        if (entry == null || path == null) {
            _ai.value = AiUi.Error(JournalRu.noEntriesHere)
            return
        }
        viewModelScope.launch {
            _ai.value = AiUi.Loading
            val log = (app as Step4App).activityLog
            val key = "ai-journal-help-entry-$entryId"
            log.aiBegin("Дневник · помощь", key, path.current.displayTitle())
            val program = currentProgram()
            val personality = personalityForPrompt()
            val questionnaire = LifeBoardPrompts.merge(
                JournalPrompts.formatQuestionnaire(prefs.profile),
                (app as Step4App).lifeBoard.goalsPromptBlock()
            )
            val user = JournalPrompts.helpEntryUser(
                path = path,
                entry = entry,
                personality = personality,
                questionnaire = questionnaire
            )
            val result = withContext(Dispatchers.IO) {
                JournalAiClient.chat(
                    user = user,
                    role = "journal.help_entry",
                    program = program,
                    premium = premiumActive(),
                    admin = prefs.isAdmin
                )
            }
            log.aiDone(key, path.current.displayTitle())
            _ai.value = when (result) {
                is JournalAiClient.Result.Ok -> {
                    prefs.consumeAi()
                    refreshMeta()
                    AiUi.Ready(result.text, null, fromCache = false, prompt = result.prompt)
                }
                is JournalAiClient.Result.Err -> AiUi.Error(result.message)
            }
        }
    }

    fun requestAnalyze(entryId: String?, forceRefresh: Boolean = false) {
        if (!forceRefresh && entryId != null) {
            val entry = store.byId(entryId)
            val cached = entry?.let { analyzeCacheFresh(it) }
            if (cached != null) {
                _ai.value = AiUi.Ready(cached.text, null, fromCache = true)
                return
            }
        }
        if (!prefs.canUseAi()) {
            _ai.value = AiUi.Error(JournalRu.aiLimit)
            return
        }
        val snap = state.value
        val entries = if (entryId != null) {
            listOfNotNull(store.byId(entryId))
        } else {
            val id = snap.path?.current?.id ?: return
            store.entriesFor(id)
        }
        val path = if (entryId != null) {
            entries.firstOrNull()?.let { catalog.pathOf(it.nodeId) }
        } else {
            snap.path
        } ?: return
        if (entries.isEmpty()) {
            _ai.value = AiUi.Error(JournalRu.noEntriesHere)
            return
        }
        viewModelScope.launch {
            _ai.value = AiUi.Loading
            val log = (app as Step4App).activityLog
            val key = "ai-journal-analyze-${entryId ?: path.current.id}"
            log.aiBegin("Дневник · ИИ-анализ", key, path.current.displayTitle())
            val program = currentProgram()
            val personality = personalityForAnalyze()
            val questionnaire = LifeBoardPrompts.merge(
                JournalPrompts.formatQuestionnaire(prefs.profile),
                (app as Step4App).lifeBoard.goalsPromptBlock()
            )
            val user = JournalPrompts.analyzeUser(
                path = path,
                entries = entries,
                personality = personality,
                questionnaire = questionnaire,
                singleEntry = entryId != null,
                collectPersonality = prefs.profile.personalityCollectEnabled
            )
            val result = withContext(Dispatchers.IO) {
                JournalAiClient.chat(
                    user = user,
                    role = "journal.analyze",
                    program = program,
                    premium = premiumActive(),
                    admin = prefs.isAdmin
                )
            }
            log.aiDone(key, path.current.displayTitle())
            _ai.value = when (result) {
                is JournalAiClient.Result.Ok -> {
                    prefs.consumeAi()
                    refreshMeta()
                    val eventId = "journal-analyze-" + (entryId ?: path.current.id) + "-" +
                        entries.joinToString("-") { it.id }.hashCode()
                    val parsed = JournalPrompts.parsePersonality(result.text)
                    val visible = (app as Step4App).spiritualRating.consumeAiText(eventId, parsed.first)
                    if (prefs.profile.personalityCollectEnabled && !parsed.second.isNullOrBlank()) {
                        applyPortrait(parsed.second.orEmpty())
                    }
                    if (entryId != null) {
                        entries.firstOrNull()?.let {
                            (app as Step4App).journalAnalyzeCache.save(it, visible)
                        }
                    }
                    AiUi.Ready(visible, parsed.second, fromCache = false, prompt = result.prompt)
                }
                is JournalAiClient.Result.Err -> AiUi.Error(result.message)
            }
        }
    }

    private fun currentProgram(): String = prefs.profile.program.ifBlank {
        prefs.questionnaireAnswers()["section1:program_type"].orEmpty()
    }

    private fun personalityForPrompt(): String? =
        if (prefs.profile.personalityEnabled) {
            prefs.profile.personality.trim().ifBlank { "(пока не заполнено)" }
        } else {
            null
        }

    private fun personalityForAnalyze(): String? =
        personalityForPrompt()
            ?: if (prefs.profile.personalityCollectEnabled) {
                prefs.profile.personality.trim().ifBlank { "(пока не заполнено)" }
            } else {
                null
            }

    fun applyPortrait(text: String) {
        prefs.personality = text.trim()
        refreshMeta()
    }

    fun resetAi() {
        _ai.value = AiUi.Idle
    }

    fun countFor(nodeId: Int): Int =
        catalog.countInSubtree(nodeId, store.entries.value)

    fun entriesForNode(nodeId: Int): List<JournalEntry> {
        val ids = catalog.descendantIds(nodeId)
        return store.entries.value
            .filter { it.nodeId in ids }
            .sortedByDescending { it.createdAt }
    }

    fun analyzeCacheFor(entryId: String): CachedEntryAnalyze? =
        (app as Step4App).journalAnalyzeCache.get(entryId)

    fun analyzeCacheFresh(entry: JournalEntry): CachedEntryAnalyze? {
        val cached = analyzeCacheFor(entry.id) ?: return null
        return if (cached.matches(entry)) cached else null
    }

    private var analyzeForceOnce = false

    fun prepareAnalyzeNavigation(forceNew: Boolean) {
        analyzeForceOnce = forceNew
    }

    fun consumeAnalyzeForce(): Boolean {
        val value = analyzeForceOnce
        analyzeForceOnce = false
        return value
    }

    fun showCachedAnalyze(entryId: String): Boolean {
        val cached = analyzeCacheFor(entryId) ?: return false
        _ai.value = AiUi.Ready(cached.text, null, fromCache = true)
        return true
    }

    /** Place + draft for editing without wiping the draft. */
    fun editEntryFromPick(entry: JournalEntry) {
        setCurrentPlace(entry.nodeId, clearDraft = false)
        startEdit(entry)
    }

    fun formatDate(millis: Long): String {
        val locale = ru.na.step4.obidy.data.i18n.I18n.locale()
        return SimpleDateFormat("d MMMM yyyy, HH:mm", locale).format(Date(millis))
    }

    fun showNotice(text: String) {
        _meta.update { it.copy(notice = text) }
    }

    fun suggestedExportName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "12steps-zapisi-$stamp.json"
    }

    fun exportToDownloads(context: Context, share: Boolean = true) {
        viewModelScope.launch {
            val count = store.entries.value.size
            if (count == 0) {
                _meta.update { it.copy(notice = JournalRu.exportJsonEmpty) }
                return@launch
            }
            val result = try {
                val json = store.exportJson { id -> catalog.pathOf(id)?.line() }
                val name = suggestedExportName()
                val (uri, savedName) = withContext(Dispatchers.IO) {
                    ru.na.step4.obidy.data.files.DownloadJson.write(
                        context,
                        name,
                        json.toByteArray(Charsets.UTF_8)
                    )
                }
                if (share) {
                    ru.na.step4.obidy.data.files.DownloadJson.share(
                        context,
                        uri,
                        savedName
                    )
                }
                String.format(JournalRu.exportJsonOk, savedName, count)
            } catch (_: Exception) {
                JournalRu.exportJsonError
            }
            _meta.update { it.copy(notice = result) }
        }
    }

    fun importFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error("empty")
                }
                val imported = store.importFromJson(text)
                if (imported.total == 0) JournalRu.importJsonEmpty
                else String.format(JournalRu.importJsonOk, imported.added, imported.updated)
            } catch (_: Exception) {
                JournalRu.importJsonError
            }
            _meta.update { it.copy(notice = result) }
        }
    }

    private fun premiumActive(): Boolean =
        prefs.isPro || prefs.isAdmin || (app as Step4App).psychSettings.isPro

    private fun markJournalWriting() {
        val title = state.value.path?.current?.displayTitle().orEmpty()
        (app as Step4App).activityLog.journalWriteStart(title)
    }

    private fun syncPsychPremium(on: Boolean) {
        val psych = (app as Step4App).psychSettings
        if (on) {
            if (!psych.isPro) psych.grantProDays(365)
        } else {
            psych.proExpiryMillis = 0L
        }
    }

    private fun refreshMeta() {
        _meta.update { old ->
            readMeta().copy(
                draft = old.draft,
                fieldValues = old.fieldValues,
                lastSaved = old.lastSaved,
                editingId = old.editingId,
                notice = old.notice
            )
        }
    }

    private fun readMeta(): JournalState {
        val currentId = prefs.currentId
        return JournalState(
            registered = prefs.registered,
            name = prefs.name,
            problems = prefs.problems,
            path = if (currentId > 0) catalog.pathOf(currentId) else null,
            personality = prefs.personality,
            personalityEnabled = prefs.personalityEnabled,
            personalityCollectEnabled = prefs.profile.personalityCollectEnabled,
            isPro = prefs.isPro,
            isAdmin = prefs.isAdmin,
            remainingAi = if (prefs.isAdmin) Int.MAX_VALUE else prefs.remainingAiToday(),
            draft = prefs.draft,
            splitFields = prefs.splitFields,
            fields = prefs.fields,
            fieldValues = prefs.fieldValues,
            editingId = prefs.editingId.takeIf { it.isNotBlank() }
        )
    }

    private fun persistDraft(text: String, editingId: String?) {
        prefs.draft = text
        prefs.editingId = editingId.orEmpty()
    }

    companion object {
        fun factory(app: Application, store: JournalStore, prefs: JournalPrefs) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return JournalViewModel(app, store, prefs) as T
                }
            }
    }
}
