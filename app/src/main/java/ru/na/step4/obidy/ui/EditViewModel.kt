package ru.na.step4.obidy.ui

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

data class EditUiState(
    val id: Long = 0,
    val categoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val knownTargets: List<String> = emptyList(),
    val target: String = "",
    val situations: List<Situation> = emptyList(),
    val notes: String = "",
    val isCompleted: Boolean = false,
    val loaded: Boolean = false,
    val saved: Boolean = false,
    val quickSituation: String = ""
) {
    val progress: Int
        get() {
            var n = 0
            if (target.isNotBlank()) n++
            n += situations.sumOf { it.progressSteps }
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

    private val situationsFlow = form.map { it.id }.flatMapLatest { id ->
        if (id > 0) repository.observeSituationsForResentment(id) else flowOf(emptyList())
    }

    private val knownTargetsFlow = repository.observeAll().map { list ->
        list.map { it.target.trim() }.filter { it.isNotEmpty() }.distinct()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }

    val uiState: StateFlow<EditUiState> = combine(
        form,
        repository.observeCategories(),
        knownTargetsFlow,
        situationsFlow
    ) { state, categories, knownTargets, situations ->
        state.copy(
            categories = categories,
            knownTargets = knownTargets,
            situations = situations
        )
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
            viewModelScope.launch {
                val defaultId = repository.defaultCategoryId()
                form.update { it.copy(loaded = true, categoryId = defaultId) }
            }
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

    fun addBlankSituation(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val rid = ensureResentmentId()
            val id = repository.addSituation(rid)
            onCreated(id)
        }
    }

    fun addSituationFromQuick(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val text = form.value.quickSituation.trim()
            if (text.isEmpty()) return@launch
            val rid = ensureResentmentId()
            val id = repository.addSituation(rid, whatHappened = text)
            form.update { it.copy(quickSituation = "") }
            onCreated(id)
        }
    }

    fun deleteSituation(situation: Situation) {
        viewModelScope.launch {
            repository.deleteSituation(situation)
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
