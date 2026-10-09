package ru.na.step4.obidy.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/**
 * Черновик новой главы. Живёт в ViewModel, а не в экране, поэтому переживает
 * уход с экрана и поворот, а текст дописывается в базу сам.
 */
data class BookNewChapterState(
    val title: String = "",
    val text: String = "",
    val chapterId: Long? = null
)

class BookViewModel(private val repository: BookRepository) : ViewModel() {

    val books: StateFlow<List<Book>> = repository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _edit = MutableStateFlow(BookEditState())
    val edit: StateFlow<BookEditState> = _edit.asStateFlow()

    private val _newChapter = MutableStateFlow(BookNewChapterState())
    val newChapter: StateFlow<BookNewChapterState> = _newChapter.asStateFlow()

    private var bookJob: Job? = null
    private var chaptersJob: Job? = null
    private var openBookId: Long? = null
    private var selectAfterCreate: Long? = null
    private var dirty = false

    private var autosaveJob: Job? = null
    private var newChapterBookId: Long? = null
    private var newChapterAutosaveJob: Job? = null
    private var newChapterWriteJob: Job? = null

    /** Меняется при освобождении полей: запись не должна достаться следующему черновику. */
    private var newChapterToken = 0L

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
        autosaveJob?.cancel()
        autosaveJob = null
        saveDraftNow()
        newChapterAutosaveJob?.cancel()
        newChapterAutosaveJob = null
        bookJob?.cancel()
        chaptersJob?.cancel()
        bookJob = null
        chaptersJob = null
        openBookId = null
        selectAfterCreate = null
        dirty = false
        _edit.value = BookEditState()
    }

    /** Открыть другую главу: текст берётся из базы, правки предыдущей сначала дописываются. */
    fun selectChapter(chapterId: Long) {
        val state = _edit.value
        val chapter = state.chapters.firstOrNull { it.id == chapterId } ?: return
        flushDraft()
        dirty = false
        _edit.value = state.copy(chapterId = chapterId, draft = chapter.text)
    }

    fun updateDraft(text: String) {
        dirty = true
        _edit.value = _edit.value.copy(draft = text)
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            saveDraftNow()
        }
    }

    /** Дописать несохранённые правки: смена главы и уход с экрана. */
    fun flushDraft() {
        autosaveJob?.cancel()
        autosaveJob = null
        saveDraftNow()
    }

    /** Снимок берётся сразу, запись идёт в фоне — состояние можно сбрасывать следом. */
    private fun saveDraftNow() {
        val state = _edit.value
        val chapter = state.chapter ?: return
        if (!dirty) return
        dirty = false
        viewModelScope.launch { repository.saveChapter(chapter.id, chapter.title, state.draft) }
    }

    fun saveDraft(onDone: (Boolean) -> Unit = {}) {
        autosaveJob?.cancel()
        autosaveJob = null
        val state = _edit.value
        val chapter = state.chapter
        if (chapter == null || state.saving) {
            onDone(false)
            return
        }
        dirty = false
        _edit.value = state.copy(saving = true)
        viewModelScope.launch {
            repository.saveChapter(chapter.id, chapter.title, state.draft)
            _edit.value = _edit.value.copy(saving = false)
            onDone(true)
        }
    }

    // --- Черновик новой главы: пишется сам, чтобы написанное не пропадало ---

    /** Подготовить поля для новой главы: уже начатый черновик этой же книги продолжается. */
    fun startNewChapter(bookId: Long) {
        if (newChapterBookId == bookId) return
        saveNewChapterNow()
        newChapterBookId = bookId
        resetNewChapter()
    }

    fun updateNewChapterTitle(value: String) {
        _newChapter.value = _newChapter.value.copy(title = value.take(TITLE_LIMIT))
        scheduleNewChapterSave()
    }

    fun updateNewChapterText(value: String) {
        _newChapter.value = _newChapter.value.copy(text = value)
        scheduleNewChapterSave()
    }

    /** Записать черновик немедленно: уход с экрана, смена книги, закрытие. */
    fun saveNewChapterNow() {
        newChapterAutosaveJob?.cancel()
        newChapterAutosaveJob = null
        val draft = _newChapter.value
        if (draft.text.isBlank()) return
        launchNewChapterWrite(draft, newChapterToken)
    }

    /**
     * Закончить черновик: записать написанное и освободить поля под следующую главу.
     * [onDone] получает true, если текст сохранён (в том числе если глава уже была записана).
     */
    fun finishNewChapter(onDone: (Boolean) -> Unit) {
        newChapterAutosaveJob?.cancel()
        newChapterAutosaveJob = null
        val draft = _newChapter.value
        val bookId = newChapterBookId
        if (draft.text.isBlank()) {
            val savedBefore = draft.chapterId != null
            if (savedBefore) resetNewChapter()
            onDone(savedBefore)
            return
        }
        if (bookId == null) {
            onDone(false)
            return
        }
        val token = newChapterToken
        launchNewChapterWrite(draft, token) { id ->
            if (token == newChapterToken) resetNewChapter()
            onDone(id != null)
        }
    }

    private fun resetNewChapter() {
        newChapterToken += 1
        _newChapter.value = BookNewChapterState()
    }

    private fun scheduleNewChapterSave() {
        if (newChapterBookId == null) return
        newChapterAutosaveJob?.cancel()
        newChapterAutosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            saveNewChapterNow()
        }
    }

    /** Предыдущая запись дожидается: иначе две вставки подряд создадут две главы. */
    private fun launchNewChapterWrite(
        draft: BookNewChapterState,
        token: Long,
        onDone: (Long?) -> Unit = {}
    ) {
        val bookId = newChapterBookId
        if (bookId == null) {
            onDone(null)
            return
        }
        val previous = newChapterWriteJob
        newChapterWriteJob = viewModelScope.launch {
            previous?.join()
            val id = writeNewChapter(bookId, draft)
            if (id != null && draft.chapterId == null && token == newChapterToken) {
                _newChapter.value = _newChapter.value.copy(chapterId = id)
            }
            onDone(id)
        }
    }

    private suspend fun writeNewChapter(bookId: Long, draft: BookNewChapterState): Long? {
        // Глава могла быть создана предыдущей записью: тогда дописываем её, а не заводим вторую.
        val existing = draft.chapterId ?: _newChapter.value.chapterId
        if (existing != null) {
            // Пока название пустое, имя главы не трогаем: его подставили при создании.
            if (draft.title.isBlank()) {
                repository.saveChapterText(existing, draft.text)
            } else {
                repository.saveChapter(existing, draft.title, draft.text)
            }
            return existing
        }
        val order = _edit.value.chapters.size + 1
        val name = draft.title.trim().ifBlank { BookRu.chapterName(order) }
        val created = repository.createChapter(bookId, name, draft.text)
        selectAfterCreate = created
        return created
    }

    fun createBook(title: String, onCreated: (Long?) -> Unit) {
        val name = title.trim()
        if (name.isEmpty()) {
            onCreated(null)
            return
        }
        viewModelScope.launch { onCreated(repository.createBook(name)) }
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }

    companion object {
        private const val AUTOSAVE_DELAY_MS = 800L
        private const val TITLE_LIMIT = 120

        fun factory(repository: BookRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BookViewModel(repository) as T
            }
        }
    }
}
