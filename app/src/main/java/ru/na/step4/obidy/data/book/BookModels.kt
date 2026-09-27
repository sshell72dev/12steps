package ru.na.step4.obidy.data.book

/** Книга пользователя: обложка-название и главы. */
data class Book(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val chapterCount: Int = 0
) {
    val isEmpty: Boolean get() = chapterCount == 0
}

/** Глава книги: название и текст. */
data class BookChapter(
    val id: Long,
    val bookId: Long,
    val title: String,
    val text: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** Первые строки текста для списка глав. */
    val preview: String
        get() = text.replace(WHITESPACE, " ").trim().take(140)

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
