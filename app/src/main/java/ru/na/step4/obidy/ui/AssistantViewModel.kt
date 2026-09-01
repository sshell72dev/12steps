package ru.na.step4.obidy.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.assistant.AssistantBrief
import ru.na.step4.obidy.assistant.ChatTurn
import ru.na.step4.obidy.assistant.DialogSession
import ru.na.step4.obidy.assistant.LocalFunnel
import ru.na.step4.obidy.assistant.VapiVoiceController
import ru.na.step4.obidy.assistant.VoiceUiState
import ru.na.step4.obidy.data.Category
import ru.na.step4.obidy.data.QuestionFocus
import ru.na.step4.obidy.data.Resentment
import ru.na.step4.obidy.data.ResentmentRepository
import ru.na.step4.obidy.data.Situation

data class QuestionAssistState(
    val situationId: Long = 0,
    val resentmentId: Long = 0,
    val target: String = "",
    val focusKey: String = "",
    val focusTitle: String = "",
    val focusHint: String = "",
    val currentAnswer: String = "",
    val situationAnswers: String = ""
) {
    val active: Boolean get() = situationId > 0 && focusKey.isNotBlank()
}

data class AssistantUiState(
    val session: DialogSession = DialogSession(),
    val input: String = "",
    val voice: VoiceUiState = VoiceUiState(),
    val inventoryTotal: Int = 0,
    val inventoryDone: Int = 0,
    val categoryNames: String = "",
    val questionAssist: QuestionAssistState = QuestionAssistState()
)

class AssistantViewModel(
    app: Application,
    private val repository: ResentmentRepository,
    private val focusSituationId: Long = -1L,
    private val focusKey: String = ""
) : AndroidViewModel(app) {

    private val voice = VapiVoiceController(
        scope = viewModelScope,
        plugin = (app as? ru.na.step4.obidy.Step4App)?.voicePlugin
    )

    private val session = MutableStateFlow(DialogSession())
    private val input = MutableStateFlow("")
    private val questionAssist = MutableStateFlow(QuestionAssistState())

    private val inventory = combine(
        repository.observeCount(),
        repository.observeCompletedCount(),
        repository.observeCategories()
    ) { total, done, categories ->
        Triple(total, done, categories.joinToString(", ", transform = Category::name))
    }

    val uiState: StateFlow<AssistantUiState> = combine(
        session,
        input,
        voice.state,
        inventory,
        questionAssist
    ) { s, inp, v, inv, qa ->
        AssistantUiState(
            session = s,
            input = inp,
            voice = v,
            inventoryTotal = inv.first,
            inventoryDone = inv.second,
            categoryNames = inv.third,
            questionAssist = qa
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUiState())

    init {
        if (focusSituationId > 0 && focusKey.isNotBlank()) {
            viewModelScope.launch { loadQuestionFocus(focusSituationId, focusKey) }
        } else if (!session.value.hasPriorDialog) {
            val opening = LocalFunnel.opening(session.value)
            session.value = session.value.copy(
                turns = listOf(ChatTurn("assistant", opening))
            )
        }
    }

    private suspend fun loadQuestionFocus(situationId: Long, key: String) {
        val situation = repository.getSituation(situationId) ?: return
        val resentment = repository.getById(situation.resentmentId)
        val title = QuestionFocus.titleOf(key)
        val hint = QuestionFocus.hintOf(key)
        val answers = QuestionFocus.buildSituationAnswersText(
            target = resentment?.target.orEmpty(),
            situation = situation,
            typeNames = emptyList()
        )
        val current = QuestionFocus.currentAnswer(situation, key)
        questionAssist.value = QuestionAssistState(
            situationId = situationId,
            resentmentId = situation.resentmentId,
            target = resentment?.target.orEmpty(),
            focusKey = key,
            focusTitle = title,
            focusHint = hint,
            currentAnswer = current,
            situationAnswers = answers
        )
        val opening = AssistantBrief.questionFocusFirstMessage(title)
        session.value = DialogSession(
            turns = listOf(ChatTurn("assistant", opening))
        )
    }

    fun attachHost(activity: Activity, lifecycle: Lifecycle) {
        voice.attach(activity, lifecycle)
    }

    fun updateInput(value: String) {
        input.value = value
    }

    fun sendText() {
        val text = input.value.trim()
        if (text.isEmpty()) return
        input.value = ""
        val qa = questionAssist.value
        if (qa.active) {
            val userTurn = ChatTurn("user", text)
            val reply = ChatTurn("assistant", Ru.questionAssistReply)
            session.value = session.value.copy(
                turns = session.value.turns + userTurn + reply
            )
        } else {
            val (next, _) = LocalFunnel.reply(session.value, text)
            session.value = next
        }
    }

    fun startVoice() {
        val state = uiState.value
        val qa = state.questionAssist
        if (qa.active) {
            val extras = mapOf(
                "category_names" to state.categoryNames.ifBlank { "(none)" },
                "resentment_context" to qa.situationAnswers,
                "resentment_target" to qa.target.ifBlank { "(не указано)" },
                "focus_question" to qa.focusTitle,
                "focus_hint" to qa.focusHint,
                "focus_current_answer" to qa.currentAnswer.ifBlank { "(пока пусто)" },
                "situation_answers" to qa.situationAnswers,
                "inventory_total" to state.inventoryTotal.toString(),
                "inventory_done" to state.inventoryDone.toString()
            )
            voice.start(session.value, extras, questionFocus = true)
        } else {
            val extras = mapOf(
                "category_names" to state.categoryNames.ifBlank { "(none)" },
                "resentment_context" to session.value.funnelSummary(),
                "inventory_total" to state.inventoryTotal.toString(),
                "inventory_done" to state.inventoryDone.toString()
            )
            voice.start(session.value, extras, questionFocus = false)
        }
    }

    fun stopVoice() = voice.stop()

    fun toggleMute() = voice.toggleMute()

    fun onMicPermissionDenied() {
        voice.setError(Ru.micPermissionNeeded)
    }

    fun createResentmentFromDraft(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val s = session.value
            val resentmentId = repository.save(
                Resentment(
                    target = s.draftTarget,
                    notes = "",
                    categoryId = repository.defaultCategoryId()
                )
            )
            var situation = Situation(
                resentmentId = resentmentId,
                whatHappened = s.draftWhat,
                iFelt = s.draftFelt,
                iDid = s.draftDid
            )
            s.draftAnswers.forEach { (number, value) -> situation = situation.withAnswer(number, value) }
            repository.saveSituation(situation)
            onCreated(resentmentId)
        }
    }

    override fun onCleared() {
        voice.release()
        super.onCleared()
    }

    companion object {
        fun factory(
            app: Application,
            repository: ResentmentRepository,
            situationId: Long = -1L,
            focusKey: String = ""
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AssistantViewModel(app, repository, situationId, focusKey) as T
            }
        }
    }
}
