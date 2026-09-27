package ru.na.step4.obidy.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.book.Book
import ru.na.step4.obidy.data.book.BookChapter
import ru.na.step4.obidy.data.book.BookRepository
import ru.na.step4.obidy.data.book.BookRu

/**
 * Открытая книга: главы и текст в поле редактора.
 * Черновик не перетирается обновлениями из базы, пока пользователь его не сохранит.
 */
data class BookEditState(
    val book: Book? = null,
    val chapters: List<BookChapter> = emptyList(),
    val chapterId: Long? = null,
    val draft: String = "",
    val saving: Boolean = false
) {
    val chapter: BookChapter? get() = chapters.firstOrNull { it.id == chapterId }
    val hasChapters: Boolean get() = chapters.isNotEmpty()
}

class BookViewModel(private val repository: BookRepository) : ViewModel() {

    val books: StateFlow<List<Book>> = repository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _edit = MutableStateFlow(BookEditState())
    val edit: StateFlow<BookEditState> = _edit.asStateFlow()

    private var bookJob: Job? = null
    private var chaptersJob: Job? = null
    private var openBookId: Long? = null
    private var selectAfterCreate: Long? = null
    private var dirty = false

    fun open(bookId: Long) {
        if (openBookId == bookId) return
        close()
        openBookId = bookId
        bookJob = viewModelScope.launch {
            repository.observeBook(bookId).collect { book ->
                _edit.value = _edit.value.copy(book = book)
            }
        }
        chaptersJob = viewModelScope.launch {
            repository.observeChapters(bookId).collect { chapters ->
                val current = _edit.value
                val created = selectAfterCreate?.takeIf { id -> chapters.any { it.id == id } }
                if (created != null) selectAfterCreate = null
                val kept = current.chapterId?.takeIf { id -> chapters.any { it.id == id } }
                val chapterId = created ?: kept ?: chapters.lastOrNull()?.id
                val sameChapter = chapterId != null && chapterId == current.chapterId
                val draft = if (sameChapter && dirty) {
                    current.draft
                } else {
                    chapters.firstOrNull { it.id == chapterId }?.text.orEmpty()
                }
                _edit.value = current.copy(chapters = chapters, chapterId = chapterId, draft = draft)
            }
        }
    }

    fun close() {
        bookJob?.cancel()
        chaptersJob?.cancel()
        bookJob = null
        chaptersJob = null
        openBookId = null
        selectAfterCreate = null
        dirty = false
        _edit.value = BookEditState()
    }

    /** Открыть другую главу: текст берётся из базы, черновик предыдущей отбрасывается. */
    fun selectChapter(chapterId: Long) {
        val chapter = _edit.value.chapters.firstOrNull { it.id == chapterId } ?: return
        dirty = false
        _edit.value = _edit.value.copy(chapterId = chapterId, draft = chapter.text)
    }

    fun updateDraft(text: String) {
        dirty = true
        _edit.value = _edit.value.copy(draft = text)
    }

    fun saveDraft(onDone: (Boolean) -> Unit = {}) {
        val state = _edit.value
        val chapter = state.chapter
        if (chapter == null || state.saving) {
            onDone(false)
            return
        }
        _edit.value = state.copy(saving = true)
        viewModelScope.launch {
            repository.saveChapter(chapter.id, chapter.title, state.draft)
            dirty = false
            _edit.value = _edit.value.copy(saving = false)
            onDone(true)
        }
    }

    fun createBook(title: String, onCreated: (Long?) -> Unit) {
        val name = title.trim()
        if (name.isEmpty()) {
            onCreated(null)
            return
        }
        viewModelScope.launch { onCreated(repository.createBook(name)) }
    }

    fun createChapter(bookId: Long, title: String, text: String, onCreated: (Long?) -> Unit) {
        if (text.isBlank()) {
            onCreated(null)
            return
        }
        val order = _edit.value.chapters.size + 1
        val name = title.trim().ifBlank { BookRu.chapterName(order) }
        viewModelScope.launch {
            val id = repository.createChapter(bookId, name, text.trim())
            selectAfterCreate = id
            onCreated(id)
        }
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }

    companion object {
        fun factory(repository: BookRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BookViewModel(repository) as T
            }
        }
    }
}
