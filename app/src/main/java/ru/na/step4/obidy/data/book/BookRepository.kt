package ru.na.step4.obidy.data.book

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookRepository(private val dao: BookDao) {

    fun observeBooks(): Flow<List<Book>> =
        dao.observeBooks().map { rows -> rows.map { it.toModel() } }

    fun observeBook(id: Long): Flow<Book?> =
        dao.observeBook(id).map { entity -> entity?.toModel() }

    fun observeChapters(bookId: Long): Flow<List<BookChapter>> =
        dao.observeChapters(bookId).map { list -> list.map { it.toModel() } }

    suspend fun createBook(title: String): Long {
        val now = System.currentTimeMillis()
        return dao.insertBook(
            BookEntity(title = title.trim().take(TITLE_LIMIT), createdAt = now, updatedAt = now)
        )
    }

    /** Возвращает id созданной главы; пустое название заменяется на «Глава N» на стороне экрана. */
    suspend fun createChapter(bookId: Long, title: String, text: String): Long {
        val now = System.currentTimeMillis()
        val order = dao.maxChapterOrder(bookId) + 1
        val id = dao.insertChapter(
            BookChapterEntity(
                bookId = bookId,
                title = title.trim().take(TITLE_LIMIT),
                text = text.trim(),
                sortOrder = order,
                createdAt = now,
                updatedAt = now
            )
        )
        dao.touchBook(bookId, now)
        return id
    }

    suspend fun saveChapter(chapterId: Long, title: String, text: String) {
        val now = System.currentTimeMillis()
        dao.updateChapter(chapterId, title.trim().take(TITLE_LIMIT), text, now)
        dao.chapter(chapterId)?.let { dao.touchBook(it.bookId, now) }
    }

    suspend fun book(id: Long): Book? = dao.book(id)?.toModel()

    suspend fun chapter(id: Long): BookChapter? = dao.chapter(id)?.toModel()

    suspend fun lastChapter(bookId: Long): BookChapter? = dao.lastChapter(bookId)?.toModel()

    suspend fun deleteBook(id: Long) = dao.deleteBook(id)

    private companion object {
        const val TITLE_LIMIT = 120
    }
}
