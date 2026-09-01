package ru.na.step4.obidy.data.psych

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "psych_situations")
data class PsychSituation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val summary: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val viaVoice: Boolean = false,
    val noHistory: Boolean = false,
    val topicId: Long? = null
)

@Entity(
    tableName = "psych_sessions",
    indices = [Index("situationId"), Index(value = ["sessionUid"], unique = true)]
)
data class PsychSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val situationId: Long,
    val sessionUid: String,
    val status: Int = STATUS_NEW,
    val sequentialWork: Int = SEQ_LIVE,
    val questionsJson: String = "[]",
    val currentIndex: Int = 0,
    val postponed: Boolean = false,
    val analyzeText: String = "",
    val analyzeSpeakable: String = "",
    val recommendText: String = "",
    val recommendSpeakable: String = "",
    val assistantText: String = "",
    val assistantSpeakable: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    companion object {
        const val STATUS_NEW = 0
        const val STATUS_ACTIVE = 1
        const val STATUS_DONE = 2
        const val SEQ_BATCH = 0
        const val SEQ_PRO = 1
        const val SEQ_LIVE = 2
    }
}

@Entity(
    tableName = "psych_answers",
    indices = [Index(value = ["sessionUid", "questionIndex"], unique = true)]
)
data class PsychAnswer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionUid: String,
    val questionIndex: Int,
    val questionText: String,
    val answerText: String,
    val viaVoice: Boolean = false
)

@Entity(
    tableName = "psych_topics",
    indices = [Index(value = ["name"], unique = true)]
)
data class PsychTopic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val summaryText: String = "",
    val useCount: Int = 0,
    val lastUsedAt: Long = 0
)

@Entity(tableName = "psych_ai_cache")
data class PsychAiCache(
    @PrimaryKey val cacheKey: String,
    val requestType: String,
    val responseText: String,
    val promptText: String = "",
    val lockUntil: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "psych_ai_usage")
data class PsychAiUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val viaVoice: Boolean = false
)

@Entity(
    tableName = "psych_situation_topics",
    primaryKeys = ["situationId", "topicId"],
    indices = [Index("topicId"), Index("situationId")]
)
data class PsychSituationTopic(
    val situationId: Long,
    val topicId: Long
)

data class PsychQa(
    val question: String,
    val answer: String
)

data class PsychTopicStory(
    val situationId: Long,
    val sessionId: Long?,
    val createdAt: Long,
    val summary: String
)
