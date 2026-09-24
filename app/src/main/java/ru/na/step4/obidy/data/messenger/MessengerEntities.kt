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
    val unread: Int
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
    val deleted: Boolean = false
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

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY id ASC")
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContacts(rows: List<MessengerContactRow>)

    @Query("DELETE FROM chats")
    suspend fun clearChats()

    @Query("DELETE FROM contacts")
    suspend fun clearContacts()
}
