package ru.na.step4.obidy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.Category
import ru.na.step4.obidy.data.ResentmentRepository

data class CategoriesUiState(
    val items: List<Category> = emptyList(),
    val draftName: String = "",
    val editingId: Long? = null,
    val editingName: String = ""
)

class CategoriesViewModel(private val repository: ResentmentRepository) : ViewModel() {
    private val draft = MutableStateFlow(CategoriesUiState())

    val uiState: StateFlow<CategoriesUiState> = kotlinx.coroutines.flow.combine(
        repository.observeCategories(),
        draft
    ) { items, local ->
        local.copy(items = items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    private val _confirmDelete = MutableStateFlow<Category?>(null)
    val confirmDelete: StateFlow<Category?> = _confirmDelete.asStateFlow()

    fun updateDraftName(value: String) {
        draft.update { it.copy(draftName = value) }
    }

    fun addCategory() {
        viewModelScope.launch {
            val name = draft.value.draftName.trim()
            if (name.isEmpty()) return@launch
            repository.saveCategory(Category(name = name))
            draft.update { it.copy(draftName = "") }
        }
    }

    fun startEdit(item: Category) {
        draft.update { it.copy(editingId = item.id, editingName = item.name) }
    }

    fun updateEditingName(value: String) {
        draft.update { it.copy(editingName = value) }
    }

    fun cancelEdit() {
        draft.update { it.copy(editingId = null, editingName = "") }
    }

    fun saveEdit() {
        viewModelScope.launch {
            val local = draft.value
            val id = local.editingId ?: return@launch
            val name = local.editingName.trim()
            if (name.isEmpty()) return@launch
            val existing = local.items.find { it.id == id } ?: return@launch
            repository.saveCategory(existing.copy(name = name))
            draft.update { it.copy(editingId = null, editingName = "") }
        }
    }

    fun requestDelete(item: Category) {
        _confirmDelete.value = item
    }

    fun dismissDelete() {
        _confirmDelete.value = null
    }

    fun confirmDelete() {
        viewModelScope.launch {
            val item = _confirmDelete.value ?: return@launch
            repository.deleteCategory(item)
            _confirmDelete.value = null
        }
    }

    companion object {
        fun factory(repository: ResentmentRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CategoriesViewModel(repository) as T
            }
        }
    }
}
