package ru.na.step4.obidy.data.messenger

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chats")
data class MessengerChatRow(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val peerId: String,
    val groupId: String,
    val avatarUrl: String,
    val isOwner: Boolean,
    val lastBody: String,
    val lastKind: String,
    val lastAt: Long,
    val unread: Int,
    val pinnedId: Long = 0L,
    val pinnedKind: String = "",
    val pinnedBody: String = "",
    val pinnedSender: String = "",
    val hasTopics: Boolean = false,
    /** В группе включён режим анонимности: сообщения можно писать от лица «Анонимный». */
    val anonymous: Boolean = false
)

@Entity(tableName = "messages")
data class MessengerMessageRow(
    @PrimaryKey val id: Long,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val kind: String,
    val body: String,
    val voiceDurationMs: Int,
    val createdAt: Long,
    val mine: Boolean,
    val editedAt: Long = 0L,
    val deleted: Boolean = false,
    val replyToId: Long = 0L,
    val replySenderName: String = "",
    val replyBody: String = "",
    val replyKind: String = "",
    val replyVoiceMs: Int = 0,
    val replyDeleted: Boolean = false,
    val forwardFrom: String = "",
    val reactions: String = ""
)

@Entity(tableName = "contacts")
data class MessengerContactRow(
    @PrimaryKey val id: String,
    val displayName: String
)

@Dao
interface MessengerDao {
    @Query("SELECT * FROM chats ORDER BY lastAt DESC")
    fun observeChats(): Flow<List<MessengerChatRow>>

    /**
     * Лента строится по времени создания: у локальных оповещений id отрицательный,
     * поэтому сортировка только по id показывала их в обратном порядке.
     */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(chatId: String): Flow<List<MessengerMessageRow>>

    @Query("SELECT COALESCE(MAX(id), 0) FROM messages WHERE chatId = :chatId")
    suspend fun lastMessageId(chatId: String): Long

    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    fun observeContacts(): Flow<List<MessengerContactRow>>

    @Query("SELECT * FROM chats WHERE id = :id LIMIT 1")
    suspend fun chatById(id: String): MessengerChatRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChats(rows: List<MessengerChatRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(rows: List<MessengerMessageRow>)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearMessages(chatId: String)

    /** Заменяет ленту чата целиком: так доходят правки и удаления сообщений. */
    @Transaction
    suspend fun replaceMessages(chatId: String, rows: List<MessengerMessageRow>) {
        clearMessages(chatId)
        upsertMessages(rows)
    }

    @Query("SELECT id FROM chats")
    suspend fun chatIds(): List<String>

    @Query("DELETE FROM chats WHERE id IN (:ids)")
    suspend fun deleteChats(ids: List<String>)

    @Query("DELETE FROM messages WHERE chatId IN (:ids)")
    suspend fun clearMessagesFor(ids: List<String>)

    /**
     * Полная сверка списка: чаты, которых больше нет на сервере (удалённая группа,
     * выход из группы, исключение участника), уходят из кэша вместе с лентой.
     */
    @Transaction
    suspend fun syncChats(rows: List<MessengerChatRow>, keepIds: List<String>) {
        upsertChats(rows)
        val keep = keepIds.toSet()
        val stale = chatIds().filterNot { it in keep }
        if (stale.isNotEmpty()) {
            deleteChats(stale)
            clearMessagesFor(stale)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContacts(rows: List<MessengerContactRow>)

    @Query("DELETE FROM chats")
    suspend fun clearChats()

    @Query("DELETE FROM contacts")
    suspend fun clearContacts()
}
