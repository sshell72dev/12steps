# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy")


def esc(s: str) -> str:
    return "".join(f"\\u{ord(c):04x}" if ord(c) > 127 else c for c in s)


def ensure_ru():
    ru_path = ROOT / "Ru.kt"
    ru = ru_path.read_text(encoding="utf-8")
    additions = [
        ("customTypeTitle", "Свой тип ситуации"),
        ("noTypesYet", "Выберите или добавьте тип ситуации"),
        ("noSituationsYet", "Пока нет ситуаций — добавьте первую"),
        ("confirm", "Готово"),
    ]
    for key, text in additions:
        marker = f"const val {key}"
        if marker in ru:
            print("exists", key)
            continue
        line = f'    const val {key} = "{esc(text)}"\n'
        ru = ru.replace("    const val typesHint", line + "    const val typesHint", 1)
        print("added", key)
    ru_path.write_text(ru, encoding="utf-8")


EDIT_VM = r'''package ru.na.step4.obidy.ui

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
import ru.na.step4.obidy.data.TypeWithSituations

data class EditUiState(
    val id: Long = 0,
    val categoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val target: String = "",
    val tree: List<TypeWithSituations> = emptyList(),
    val notes: String = "",
    val isCompleted: Boolean = false,
    val loaded: Boolean = false,
    val saved: Boolean = false
) {
    val progress: Int
        get() {
            var n = 0
            if (target.isNotBlank()) n++
            n += tree.sumOf { branch -> branch.situations.sumOf { it.progressSteps } }
            return n
        }

    val totalSteps: Int
        get() {
            val situations = tree.sumOf { it.situations.size }
            return 1 + situations.coerceAtLeast(1) * Situation.TOTAL_STEPS
        }
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

    val uiState: StateFlow<EditUiState> = combine(
        form,
        repository.observeCategories(),
        treeFlow
    ) { state, categories, tree ->
        state.copy(categories = categories, tree = tree)
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
        viewModelScope.launch {
            repository.deleteType(type)
        }
    }

    fun addSituation(typeId: Long, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            ensureResentmentId()
            val id = repository.addSituation(typeId)
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
        if (state.id != id) {
            form.update { it.copy(id = id) }
        }
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
'''

LIST_VM = r'''package ru.na.step4.obidy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.Category
import ru.na.step4.obidy.data.Resentment
import ru.na.step4.obidy.data.ResentmentListItem
import ru.na.step4.obidy.data.ResentmentRepository

sealed interface CategoryFilter {
    data object All : CategoryFilter
    data object Uncategorized : CategoryFilter
    data class ById(val id: Long) : CategoryFilter
}

data class ListUiState(
    val items: List<ResentmentListItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filter: CategoryFilter = CategoryFilter.All,
    val total: Int = 0,
    val completed: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModel(private val repository: ResentmentRepository) : ViewModel() {
    private val filter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)

    private val itemsFlow = filter.flatMapLatest { selected ->
        val base = when (selected) {
            CategoryFilter.All -> repository.observeAll()
            CategoryFilter.Uncategorized -> repository.observeUncategorized()
            is CategoryFilter.ById -> repository.observeByCategory(selected.id)
        }
        combine(base, repository.observeTreeRevision()) { list, _ -> list }
            .mapLatest { list -> list.map { repository.listPreview(it) } }
    }

    private val totalFlow = filter.flatMapLatest { selected ->
        when (selected) {
            CategoryFilter.All -> repository.observeCount()
            CategoryFilter.Uncategorized -> repository.observeUncategorizedCount()
            is CategoryFilter.ById -> repository.observeCountByCategory(selected.id)
        }
    }

    private val completedFlow = filter.flatMapLatest { selected ->
        when (selected) {
            CategoryFilter.All -> repository.observeCompletedCount()
            CategoryFilter.Uncategorized -> repository.observeUncategorizedCompletedCount()
            is CategoryFilter.ById -> repository.observeCompletedCountByCategory(selected.id)
        }
    }

    val uiState: StateFlow<ListUiState> = combine(
        itemsFlow,
        repository.observeCategories(),
        filter,
        totalFlow,
        completedFlow
    ) { items, categories, selected, total, completed ->
        ListUiState(
            items = items,
            categories = categories,
            filter = selected,
            total = total,
            completed = completed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    fun setFilter(value: CategoryFilter) {
        filter.value = value
    }

    fun createBlank(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val categoryId = when (val selected = filter.value) {
                is CategoryFilter.ById -> selected.id
                else -> null
            }
            val id = repository.save(Resentment(categoryId = categoryId))
            onCreated(id)
        }
    }

    fun delete(item: Resentment) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }

    companion object {
        fun factory(repository: ResentmentRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ListViewModel(repository) as T
            }
        }
    }
}
'''


def patch_list_screen():
    path = ROOT / "ui/ListScreen.kt"
    t = path.read_text(encoding="utf-8")
    if "ResentmentListItem" not in t:
        t = t.replace(
            "import ru.na.step4.obidy.data.Resentment\n",
            "import ru.na.step4.obidy.data.Resentment\nimport ru.na.step4.obidy.data.ResentmentListItem\n",
        )
    t = t.replace(
        "private fun ResentmentCard(\n    index: Int,\n    item: Resentment,",
        "private fun ResentmentCard(\n    index: Int,\n    item: ResentmentListItem,",
    )
    # Call site may pass resentment - find usages
    t = t.replace(
        "ResentmentCard(\n                    index = index + 1,\n                    item = item,",
        "ResentmentCard(\n                    index = index + 1,\n                    item = item,",
    )
    # Body fields
    old_body = """                Text(
                    text = item.target.ifBlank { Ru.untitled },
                    style = MaterialTheme.typography.titleMedium,
                    color = Forest,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.whatHappened.ifBlank { item.cause }.ifBlank { Ru.causeEmpty },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = Ru.delete, tint = Danger)
            }
            Icon(
                imageVector = if (item.isCompleted) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (item.isCompleted) Moss else MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        ProgressBar(current = item.progressSteps)"""

    new_body = """                Text(
                    text = item.resentment.target.ifBlank { Ru.untitled },
                    style = MaterialTheme.typography.titleMedium,
                    color = Forest,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.preview.ifBlank { Ru.causeEmpty },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = Ru.delete, tint = Danger)
            }
            Icon(
                imageVector = if (item.resentment.isCompleted) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (item.resentment.isCompleted) Moss else MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        ProgressBar(current = item.progress, total = item.totalSteps)"""

    if old_body not in t:
        print("ListScreen body block not found exactly")
        # try softer replacements
        t = t.replace("item.target.ifBlank", "item.resentment.target.ifBlank")
        t = t.replace(
            "item.whatHappened.ifBlank { item.cause }.ifBlank { Ru.causeEmpty }",
            "item.preview.ifBlank { Ru.causeEmpty }",
        )
        t = t.replace("if (item.isCompleted)", "if (item.resentment.isCompleted)")
        t = t.replace(
            "tint = if (item.isCompleted) Moss",
            "tint = if (item.resentment.isCompleted) Moss",
        )
        t = t.replace(
            "ProgressBar(current = item.progressSteps)",
            "ProgressBar(current = item.progress, total = item.totalSteps)",
        )
        t = t.replace(
            ".background(if (item.isCompleted)",
            ".background(if (item.resentment.isCompleted)",
        )
    else:
        t = t.replace(old_body, new_body)

    # Fix list iteration callbacks that use item.id / delete(item)
    t = t.replace("onOpen(item.id)", "onOpen(item.resentment.id)")
    t = t.replace("viewModel.delete(item)", "viewModel.delete(item.resentment)")
    t = t.replace("onClick = { onOpen(item.id) }", "onClick = { onOpen(item.resentment.id) }")
    # category lookup may use item.categoryId
    t = t.replace("item.categoryId", "item.resentment.categoryId")

    path.write_text(t, encoding="utf-8")
    print("patched ListScreen")


def main():
    ensure_ru()
    (ROOT / "ui/EditViewModel.kt").write_text(EDIT_VM, encoding="utf-8")
    print("wrote EditViewModel")
    (ROOT / "ui/ListViewModel.kt").write_text(LIST_VM, encoding="utf-8")
    print("wrote ListViewModel")
    patch_list_screen()


if __name__ == "__main__":
    main()
