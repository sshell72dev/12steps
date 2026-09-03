package ru.na.step4.obidy.data.activity

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

object ActivityCat {
    const val ANALYSIS = "analysis"
    const val PSYCH = "psych"
    const val JOURNAL = "journal"
    const val AI = "ai"
    const val LISTEN = "listen"
    const val SCREEN = "screen"
}

object ActivityType {
    const val START = "start"
    const val ANSWER = "answer"
    const val FINISH = "finish"
    const val AI = "ai"
    const val LISTEN_START = "listen_start"
    const val LISTEN_END = "listen_end"
    const val SCREEN = "screen"
    const val SAVE = "save"
}

@Entity(
    tableName = "activity_events",
    indices = [
        Index(value = ["startedAt"]),
        Index(value = ["sessionKey"])
    ]
)
data class ActivityEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val type: String,
    val label: String = "",
    val detail: String = "",
    val startedAt: Long,
    val endedAt: Long? = null,
    val sessionKey: String = ""
) {
    val durationMs: Long
        get() {
            val end = endedAt ?: return 0L
            return (end - startedAt).coerceAtLeast(0L)
        }
}

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(event: ActivityEvent): Long

    @Query("UPDATE activity_events SET endedAt = :endedAt, detail = :detail WHERE id = :id")
    suspend fun close(id: Long, endedAt: Long, detail: String)

    @Query(
        """
        SELECT * FROM activity_events
        WHERE startedAt >= :from AND startedAt < :until
        ORDER BY startedAt DESC
        """
    )
    fun observeRange(from: Long, until: Long): Flow<List<ActivityEvent>>

    @Query("SELECT * FROM activity_events WHERE sessionKey = :key AND endedAt IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun openByKey(key: String): ActivityEvent?
}
