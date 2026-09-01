package ru.na.step4.obidy.ui.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.life.LifeItem
import ru.na.step4.obidy.data.life.LifeKind
import ru.na.step4.obidy.data.life.LifeStatus
import ru.na.step4.obidy.data.life.LifeBoardStore
import ru.na.step4.obidy.data.support.SupportRepository

class LifeBoardViewModel(
    private val store: LifeBoardStore,
    val kind: String,
    private val support: SupportRepository? = null
) : ViewModel() {
    init {
        if (kind == LifeKind.IDEA) {
            viewModelScope.launch { support?.importIdeasToBoard() }
        }
    }
    val items: StateFlow<List<LifeItem>> = store.items
        .map { list ->
            val filtered = list.filter { it.kind == kind }
            if (kind == LifeKind.EVENT) {
                filtered.sortedWith(
                    compareBy<LifeItem> { it.status == LifeStatus.DONE }
                        .thenBy { it.dueAt ?: Long.MAX_VALUE }
                        .thenBy { it.createdAt.takeIf { t -> t > 0L } ?: it.updatedAt }
                )
            } else {
                filtered.sortedWith(
                    compareBy<LifeItem> { it.status == LifeStatus.DONE }
                        .thenBy { it.createdAt.takeIf { t -> t > 0L } ?: it.updatedAt }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(
        id: String?,
        title: String,
        body: String,
        status: String,
        dueAt: Long?
    ) {
        viewModelScope.launch {
            store.upsert(id, kind, title, body, status, dueAt)
        }
    }

    fun setStatus(id: String, status: String) {
        viewModelScope.launch { store.setStatus(id, status) }
    }

    fun delete(id: String) {
        viewModelScope.launch { store.delete(id) }
    }

    companion object {
        fun factory(
            store: LifeBoardStore,
            kind: String,
            support: SupportRepository? = null
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LifeBoardViewModel(store, kind, support) as T
            }
    }
}
