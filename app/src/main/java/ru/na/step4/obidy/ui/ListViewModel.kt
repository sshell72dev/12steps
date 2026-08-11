package ru.na.step4.obidy.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.Category
import ru.na.step4.obidy.data.Resentment
import ru.na.step4.obidy.data.ResentmentListItem
import ru.na.step4.obidy.data.ResentmentRepository
import ru.na.step4.obidy.data.ResentmentSearch
import ru.na.step4.obidy.data.tableimport.ResentmentTableImporter
import ru.na.step4.obidy.data.tableimport.TableSheetReader

sealed interface CategoryFilter {
    data object All : CategoryFilter
    data object Uncategorized : CategoryFilter
    data class ById(val id: Long) : CategoryFilter
}

data class ListUiState(
    val items: List<ResentmentListItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filter: CategoryFilter = CategoryFilter.All,
    val searchQuery: String = "",
    val total: Int = 0,
    val completed: Int = 0,
    val importing: Boolean = false,
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModel(private val repository: ResentmentRepository) : ViewModel() {
    private val filter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)
    private val searchQuery = MutableStateFlow("")
    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

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

    private val filteredItems = combine(itemsFlow, searchQuery) { items, query ->
        ResentmentSearch.sortItemsByTarget(
            items.filter {
                ResentmentSearch.matchesResentment(query, it.resentment.target, it.preview)
            }
        )
    }

    private val baseState = combine(
        filteredItems,
        repository.observeCategories(),
        filter,
        searchQuery,
        totalFlow
    ) { items, categories, selected, query, total ->
        ListUiState(
            items = items,
            categories = categories,
            filter = selected,
            searchQuery = query,
            total = total
        )
    }.combine(completedFlow) { base, completed ->
        base.copy(completed = completed)
    }

    val uiState: StateFlow<ListUiState> = combine(
        baseState,
        importing,
        message
    ) { base, isImporting, msg ->
        base.copy(importing = isImporting, message = msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    fun setFilter(value: CategoryFilter) {
        filter.value = value
    }

    fun setSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun clearMessage() {
        message.value = null
    }

    fun createBlank(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val categoryId = when (val selected = filter.value) {
                is CategoryFilter.ById -> selected.id
                else -> repository.defaultCategoryId()
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

    fun importFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            importing.value = true
            message.value = null
            val resultMessage = withContext(Dispatchers.IO) {
                runCatching {
                    val name = uri.lastPathSegment.orEmpty()
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("empty")
                    val rows = TableSheetReader.read(bytes, name)
                    val imported = ResentmentTableImporter(repository).importTable(rows)
                    if (imported.situationCount == 0 && imported.resentmentCount == 0) {
                        Ru.importEmpty
                    } else {
                        String.format(
                            Ru.importOk,
                            imported.resentmentCount,
                            imported.situationCount
                        )
                    }
                }.getOrElse { Ru.importError }
            }
            importing.value = false
            message.value = resultMessage
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
