# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy")
MOD = chr(77) + "odifier"


def w(rel, content):
    content = content.replace("UI_MODIFIER", MOD)
    (ROOT / rel).write_text(content, encoding="utf-8", newline="\n")
    print("wrote", rel)


w(
    "ui/EditViewModel.kt",
    r'''package ru.na.step4.obidy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.Category
import ru.na.step4.obidy.data.Resentment
import ru.na.step4.obidy.data.ResentmentRepository
import ru.na.step4.obidy.data.Situation
import ru.na.step4.obidy.data.SituationType
import ru.na.step4.obidy.data.SituationWithTypes
import ru.na.step4.obidy.data.TypeSuggestEngine
import ru.na.step4.obidy.data.TypeWithSituations

data class EditUiState(
    val id: Long = 0,
    val categoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val target: String = "",
    val tree: List<TypeWithSituations> = emptyList(),
    val situations: List<SituationWithTypes> = emptyList(),
    val notes: String = "",
    val isCompleted: Boolean = false,
    val loaded: Boolean = false,
    val saved: Boolean = false,
    val quickSituation: String = ""
) {
    val typeCatalog: List<String>
        get() = TypeSuggestEngine.catalogNames(tree.map { it.type })

    val progress: Int
        get() {
            var n = 0
            if (target.isNotBlank()) n++
            n += situations.sumOf { it.situation.progressSteps }
            return n
        }

    val totalSteps: Int
        get() = 1 + situations.size.coerceAtLeast(1) * Situation.TOTAL_STEPS
}

@OptIn(ExperimentalCoroutinesApi::class)
class EditViewModel(
    private val repository: ResentmentRepository,
    private val resentmentId: Long
) : ViewModel() {

    private val form = MutableStateFlow(EditUiState(id = resentmentId))

    private val treeFlow = form.map { it.id }.flatMapLatest { id ->
        if (id > 0) repository.observeTree(id) else flowOf(emptyList())
    }

    private val situationsFlow = form.map { it.id }.flatMapLatest { id ->
        if (id > 0) repository.observeSituationsWithTypes(id) else flowOf(emptyList())
    }

    val uiState: StateFlow<EditUiState> = combine(
        form,
        repository.observeCategories(),
        treeFlow,
        situationsFlow
    ) { state, categories, tree, situations ->
        state.copy(categories = categories, tree = tree, situations = situations)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        EditUiState(id = resentmentId)
    )

    init {
        if (resentmentId > 0) {
            viewModelScope.launch {
                repository.getById(resentmentId)?.let { item ->
                    form.update {
                        it.copy(
                            id = item.id,
                            categoryId = item.categoryId,
                            target = item.target,
                            notes = item.notes,
                            isCompleted = item.isCompleted,
                            loaded = true
                        )
                    }
                }
            }
        } else {
            form.update { it.copy(loaded = true) }
        }
    }

    fun updateTarget(value: String) = form.update { it.copy(target = value, saved = false) }
    fun updateNotes(value: String) = form.update { it.copy(notes = value, saved = false) }
    fun updateQuickSituation(value: String) = form.update { it.copy(quickSituation = value) }

    fun setCategory(categoryId: Long?) {
        form.update { it.copy(categoryId = categoryId, saved = false) }
    }

    fun toggleCompleted() {
        form.update { it.copy(isCompleted = !it.isCompleted, saved = false) }
    }

    fun addType(name: String) {
        viewModelScope.launch {
            val id = ensureResentmentId()
            if (id > 0) repository.addType(id, name)
        }
    }

    fun removeType(type: SituationType) {
        viewModelScope.launch { repository.deleteType(type) }
    }

    fun addBlankSituation(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val rid = ensureResentmentId()
            val id = repository.addSituation(rid)
            onCreated(id)
        }
    }

    /**
     * Creates a situation from quick text, then caller opens type-pick dialog.
     */
    fun addSituationFromQuick(onCreated: (Long, String) -> Unit) {
        viewModelScope.launch {
            val text = form.value.quickSituation.trim()
            if (text.isEmpty()) return@launch
            val rid = ensureResentmentId()
            val id = repository.addSituation(rid, whatHappened = text)
            form.update { it.copy(quickSituation = "") }
            onCreated(id, text)
        }
    }

    fun applyTypesForSituation(
        situationId: Long,
        selectedExistingIds: Set<Long>,
        selectedProposedNames: Set<String>
    ) {
        viewModelScope.launch {
            val rid = ensureResentmentId()
            repository.applyTypeSelection(
                rid, situationId, selectedExistingIds, selectedProposedNames
            )
        }
    }

    fun addSituationUnderType(typeId: Long, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val rid = ensureResentmentId()
            val id = repository.addSituation(rid)
            repository.linkSituationToType(id, typeId)
            onCreated(id)
        }
    }

    fun save(onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = ensureResentmentId()
            form.update { it.copy(id = id, saved = true, loaded = true) }
            onSaved(id)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val state = form.value
            if (state.id > 0) {
                repository.getById(state.id)?.let { repository.delete(it) }
            }
            onDeleted()
        }
    }

    private suspend fun ensureResentmentId(): Long {
        val state = form.value
        val entity = Resentment(
            id = state.id,
            categoryId = state.categoryId,
            target = state.target.trim(),
            notes = state.notes.trim(),
            isCompleted = state.isCompleted
        )
        val id = repository.save(entity)
        if (state.id != id) form.update { it.copy(id = id) }
        return id
    }

    companion object {
        fun factory(repository: ResentmentRepository, id: Long) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return EditViewModel(repository, id) as T
                }
            }
    }
}
''',
)

w(
    "ui/SituationEditViewModel.kt",
    r'''package ru.na.step4.obidy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
                val all = repository.observeTypes(item.resentmentId)
                // load once
                val types = kotlinx.coroutines.flow.first(repository.observeTypes(item.resentmentId))
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
            val types = kotlinx.coroutines.flow.first(repository.observeTypes(rid))
            form.update { it.copy(allTypes = types) }
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.saveSituation(form.value.toSituation().trimmed())
            onSaved()
        }
    }

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
''',
)

print("vms ok")
