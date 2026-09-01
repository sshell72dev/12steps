package ru.na.step4.obidy.data.psych

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PsychDao {
    @Insert
    suspend fun insertSituation(row: PsychSituation): Long

    @Update
    suspend fun updateSituation(row: PsychSituation)

    @Query("SELECT * FROM psych_situations WHERE id = :id")
    suspend fun getSituation(id: Long): PsychSituation?

    @Query(
        "SELECT * FROM psych_situations WHERE createdAt >= :from AND createdAt < :to ORDER BY createdAt DESC"
    )
    suspend fun situationsInRange(from: Long, to: Long): List<PsychSituation>

    @Query("SELECT * FROM psych_situations ORDER BY createdAt DESC")
    fun observeSituations(): Flow<List<PsychSituation>>

    @Insert
    suspend fun insertSession(row: PsychSession): Long

    @Update
    suspend fun updateSession(row: PsychSession)

    @Query("SELECT * FROM psych_sessions WHERE id = :id")
    suspend fun getSession(id: Long): PsychSession?

    @Query("SELECT * FROM psych_sessions WHERE situationId = :situationId ORDER BY id DESC LIMIT 1")
    suspend fun sessionForSituation(situationId: Long): PsychSession?

    @Query("SELECT * FROM psych_sessions WHERE sessionUid = :uid LIMIT 1")
    suspend fun sessionByUid(uid: String): PsychSession?

    @Query(
        "SELECT * FROM psych_sessions WHERE postponed = 1 AND status != ${PsychSession.STATUS_DONE} ORDER BY createdAt DESC"
    )
    fun observePostponed(): Flow<List<PsychSession>>

    @Query(
        "SELECT * FROM psych_sessions WHERE status = ${PsychSession.STATUS_DONE} ORDER BY completedAt DESC, createdAt DESC"
    )
    fun observeCompleted(): Flow<List<PsychSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnswer(row: PsychAnswer): Long

    @Query("SELECT * FROM psych_answers WHERE sessionUid = :uid ORDER BY questionIndex ASC")
    suspend fun answers(uid: String): List<PsychAnswer>

    @Insert
    suspend fun insertTopic(row: PsychTopic): Long

    @Update
    suspend fun updateTopic(row: PsychTopic)

    @Query("DELETE FROM psych_topics WHERE id = :id")
    suspend fun deleteTopic(id: Long)

    @Query("SELECT * FROM psych_topics ORDER BY lastUsedAt DESC, useCount DESC, name ASC")
    fun observeTopics(): Flow<List<PsychTopic>>

    @Query("SELECT * FROM psych_topics ORDER BY lastUsedAt DESC, useCount DESC, name ASC")
    suspend fun allTopics(): List<PsychTopic>

    @Query("SELECT * FROM psych_topics WHERE id = :id")
    suspend fun getTopic(id: Long): PsychTopic?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSituationTopic(row: PsychSituationTopic)

    @Query("DELETE FROM psych_situation_topics WHERE situationId = :situationId")
    suspend fun clearSituationTopics(situationId: Long)

    @Query("DELETE FROM psych_situation_topics WHERE topicId = :topicId")
    suspend fun clearTopicLinks(topicId: Long)

    @Query("SELECT topicId FROM psych_situation_topics WHERE situationId = :situationId")
    suspend fun topicIdsForSituation(situationId: Long): List<Long>

    @Query(
        """
        SELECT DISTINCT s.* FROM psych_situations s
        WHERE s.id != :excludeId AND (
            s.id IN (SELECT situationId FROM psych_situation_topics WHERE topicId = :topicId)
            OR s.topicId = :topicId
        )
        ORDER BY s.createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun recentTopicSituations(topicId: Long, excludeId: Long, limit: Int): List<PsychSituation>

    @Query(
        """
        SELECT DISTINCT s.* FROM psych_situations s
        WHERE s.id IN (SELECT situationId FROM psych_situation_topics WHERE topicId = :topicId)
           OR s.topicId = :topicId
        ORDER BY s.createdAt DESC
        """
    )
    suspend fun situationsForTopic(topicId: Long): List<PsychSituation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCache(row: PsychAiCache)

    @Query("SELECT * FROM psych_ai_cache WHERE cacheKey = :key")
    suspend fun getCache(key: String): PsychAiCache?

    @Insert
    suspend fun insertUsage(row: PsychAiUsage): Long

    @Query("SELECT COUNT(*) FROM psych_ai_usage WHERE createdAt >= :from")
    suspend fun usageSince(from: Long): Int
}
