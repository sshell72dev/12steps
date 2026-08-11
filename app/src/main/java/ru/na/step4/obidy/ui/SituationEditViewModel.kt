package ru.na.step4.obidy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.ResentmentRepository
import ru.na.step4.obidy.data.Situation
import ru.na.step4.obidy.data.SituationType

data class SituationEditUiState(
    val id: Long = 0,
    val resentmentId: Long = 0,
    val title: String = "",
    val whatHappened: String = "",
    val iFelt: String = "",
    val iDid: String = "",
    val answers: Map<Int, String> = emptyMap(),
    val linkedTypes: List<SituationType> = emptyList(),
    val allTypes: List<SituationType> = emptyList(),
    val loaded: Boolean = false
) {
    val progress: Int
        get() = toSituation().progressSteps

    val suggestText: String
        get() = listOf(title, whatHappened, iFelt).filter { it.isNotBlank() }.joinToString(" ")

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
}

class SituationEditViewModel(
    private val repository: ResentmentRepository,
    private val situationId: Long
) : ViewModel() {
    private val form = MutableStateFlow(SituationEditUiState(id = situationId))

    val uiState: StateFlow<SituationEditUiState> = combine(
        form,
        repository.observeTypesForSituation(situationId)
    ) { state, linked ->
        state.copy(linkedTypes = linked)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SituationEditUiState(id = situationId)
    )

    init {
        viewModelScope.launch {
            repository.getSituation(situationId)?.let { item ->
                val types = repository.observeTypes(item.resentmentId).first()
                form.value = item.toUiState(types)
            }
        }
    }

    fun updateTitle(value: String) = form.update { it.copy(title = value) }
    fun updateWhatHappened(value: String) = form.update { it.copy(whatHappened = value) }
    fun updateIFelt(value: String) = form.update { it.copy(iFelt = value) }
    fun updateIDid(value: String) = form.update { it.copy(iDid = value) }
    fun updateAnswer(number: Int, value: String) =
        form.update { it.copy(answers = it.answers + (number to value)) }

    fun applyTypes(selectedExistingIds: Set<Long>, selectedProposedNames: Set<String>) {
        viewModelScope.launch {
            val rid = form.value.resentmentId
            repository.applyTypeSelection(
                rid, situationId, selectedExistingIds, selectedProposedNames
            )
            val types = repository.observeTypes(rid).first()
            form.update { it.copy(allTypes = types) }
        }
    }

    fun saveThen(onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.saveSituation(form.value.toSituation().trimmed())
            val savedId = if (id > 0) id else form.value.id
            if (savedId != form.value.id) form.update { it.copy(id = savedId) }
            onSaved(savedId)
        }
    }

    fun save(onSaved: () -> Unit) = saveThen { onSaved() }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.getSituation(situationId)?.let { repository.deleteSituation(it) }
            onDeleted()
        }
    }

    private fun Situation.toUiState(allTypes: List<SituationType>) = SituationEditUiState(
        id = id,
        resentmentId = resentmentId,
        title = title,
        whatHappened = whatHappened,
        iFelt = iFelt,
        iDid = iDid,
        answers = (1..13).associateWith(::answerFor),
        allTypes = allTypes,
        loaded = true
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
        fun factory(repository: ResentmentRepository, id: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SituationEditViewModel(repository, id) as T
        }
    }
}
