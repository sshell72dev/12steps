package ru.na.step4.obidy.data.book

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "book_chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class BookChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long,
    val title: String,
    val text: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/** Книга вместе с числом глав — одним запросом для списка. */
data class BookRow(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val chapterCount: Int
)

@Dao
interface BookDao {
    @Query(
        """
        SELECT b.id AS id, b.title AS title, b.createdAt AS createdAt, b.updatedAt AS updatedAt,
               (SELECT COUNT(*) FROM book_chapters c WHERE c.bookId = b.id) AS chapterCount
        FROM books b
        ORDER BY b.updatedAt DESC, b.id DESC
        """
    )
    fun observeBooks(): Flow<List<BookRow>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun book(id: Long): BookEntity?

    @Insert
    suspend fun insertBook(book: BookEntity): Long

    @Query("UPDATE books SET title = :title, updatedAt = :at WHERE id = :id")
    suspend fun renameBook(id: Long, title: String, at: Long)

    @Query("UPDATE books SET updatedAt = :at WHERE id = :id")
    suspend fun touchBook(id: Long, at: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: Long)

    @Query("SELECT * FROM book_chapters WHERE bookId = :bookId ORDER BY sortOrder ASC, id ASC")
    fun observeChapters(bookId: Long): Flow<List<BookChapterEntity>>

    @Query("SELECT * FROM book_chapters WHERE bookId = :bookId ORDER BY sortOrder DESC, id DESC LIMIT 1")
    suspend fun lastChapter(bookId: Long): BookChapterEntity?

    @Query("SELECT * FROM book_chapters WHERE id = :id")
    suspend fun chapter(id: Long): BookChapterEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM book_chapters WHERE bookId = :bookId")
    suspend fun maxChapterOrder(bookId: Long): Int

    @Insert
    suspend fun insertChapter(chapter: BookChapterEntity): Long

    @Query("UPDATE book_chapters SET title = :title, text = :text, updatedAt = :at WHERE id = :id")
    suspend fun updateChapter(id: Long, title: String, text: String, at: Long)

    @Query("UPDATE book_chapters SET text = :text, updatedAt = :at WHERE id = :id")
    suspend fun updateChapterText(id: Long, text: String, at: Long)
}

internal fun BookEntity.toModel(chapterCount: Int = 0): Book =
    Book(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        chapterCount = chapterCount
    )

internal fun BookRow.toModel(): Book = Book(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    chapterCount = chapterCount
)

internal fun BookChapterEntity.toModel(): BookChapter =
    BookChapter(
        id = id,
        bookId = bookId,
        title = title,
        text = text,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
