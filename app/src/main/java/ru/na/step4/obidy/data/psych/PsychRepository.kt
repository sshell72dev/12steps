package ru.na.step4.obidy.data.psych

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class PsychRepository(private val dao: PsychDao) {
    fun observeSituations(): Flow<List<PsychSituation>> = dao.observeSituations()
    fun observeTopics(): Flow<List<PsychTopic>> = dao.observeTopics()
    fun observePostponed(): Flow<List<PsychSession>> = dao.observePostponed()
    fun observeCompleted(): Flow<List<PsychSession>> = dao.observeCompleted()

    suspend fun getSituation(id: Long) = dao.getSituation(id)
    suspend fun getSession(id: Long) = dao.getSession(id)
    suspend fun sessionForSituation(situationId: Long) = dao.sessionForSituation(situationId)
    suspend fun sessionByUid(uid: String) = dao.sessionByUid(uid)
    suspend fun answers(uid: String) = dao.answers(uid)
    suspend fun allTopics() = dao.allTopics()
    suspend fun getTopic(id: Long) = dao.getTopic(id)

    suspend fun saveSituation(
        text: String,
        viaVoice: Boolean,
        noHistory: Boolean,
        topicId: Long?
    ): Long = dao.insertSituation(
        PsychSituation(
            text = text.trim(),
            summary = PsychLogic.shortStory(text),
            viaVoice = viaVoice,
            noHistory = noHistory,
            topicId = topicId
        )
    )

    suspend fun setNoHistory(id: Long, value: Boolean) {
        val row = dao.getSituation(id) ?: return
        dao.updateSituation(row.copy(noHistory = value))
        if (value) dao.clearSituationTopics(id)
    }

    suspend fun attachTopics(situationId: Long, topicIds: List<Long>) {
        val row = dao.getSituation(situationId) ?: return
        dao.clearSituationTopics(situationId)
        val unique = topicIds.distinct().filter { it > 0L }
        unique.forEach { topicId ->
            dao.upsertSituationTopic(PsychSituationTopic(situationId, topicId))
            val topic = dao.getTopic(topicId) ?: return@forEach
            dao.updateTopic(
                topic.copy(
                    useCount = topic.useCount + 1,
                    lastUsedAt = System.currentTimeMillis()
                )
            )
        }
        dao.updateSituation(
            row.copy(
                topicId = unique.firstOrNull(),
                noHistory = false,
                summary = row.summary.ifBlank { PsychLogic.shortStory(row.text) }
            )
        )
    }

    suspend fun attachTopic(situationId: Long, topicId: Long?) {
        attachTopics(situationId, listOfNotNull(topicId))
    }

    suspend fun createSession(
        situationId: Long,
        uid: String,
        sequentialWork: Int
    ): PsychSession {
        val id = dao.insertSession(
            PsychSession(
                situationId = situationId,
                sessionUid = uid,
                status = PsychSession.STATUS_ACTIVE,
                sequentialWork = sequentialWork
            )
        )
        return dao.getSession(id)!!
    }

    suspend fun updateSession(session: PsychSession) = dao.updateSession(session)

    suspend fun saveAnswer(
        uid: String,
        index: Int,
        question: String,
        answer: String,
        viaVoice: Boolean
    ) {
        dao.upsertAnswer(
            PsychAnswer(
                sessionUid = uid,
                questionIndex = index,
                questionText = question,
                answerText = answer,
                viaVoice = viaVoice
            )
        )
    }

    suspend fun qaFor(uid: String): List<PsychQa> =
        dao.answers(uid).map { PsychQa(it.questionText, it.answerText) }

    suspend fun addTopic(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return 0L
        val existing = dao.allTopics().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing.id
        return dao.insertTopic(PsychTopic(name = trimmed, lastUsedAt = System.currentTimeMillis()))
    }

    suspend fun renameTopic(id: Long, name: String) {
        val topic = dao.getTopic(id) ?: return
        dao.updateTopic(topic.copy(name = name.trim()))
    }

    suspend fun deleteTopic(id: Long) {
        dao.clearTopicLinks(id)
        dao.deleteTopic(id)
    }

    suspend fun updateTopicSummary(id: Long, summary: String) {
        val topic = dao.getTopic(id) ?: return
        dao.updateTopic(topic.copy(summaryText = summary.trim()))
    }

    suspend fun topicStories(topicId: Long): List<PsychTopicStory> {
        return dao.situationsForTopic(topicId).map { sit ->
            val session = dao.sessionForSituation(sit.id)
            PsychTopicStory(
                situationId = sit.id,
                sessionId = session?.id,
                createdAt = sit.createdAt,
                summary = sit.summary.ifBlank { PsychLogic.shortStory(sit.text) }
            )
        }
    }

    suspend fun lastStorySnippet(topicId: Long): String {
        val row = dao.situationsForTopic(topicId).firstOrNull() ?: return ""
        return row.summary.ifBlank { PsychLogic.shortStory(row.text) }
    }

    suspend fun topicPayload(situation: PsychSituation): JSONObject? {
        val arr = topicsPayload(situation)
        return arr?.optJSONObject(0)
    }

    suspend fun topicsPayload(situation: PsychSituation): JSONArray? {
        val ids = dao.topicIdsForSituation(situation.id).ifEmpty {
            listOfNotNull(situation.topicId)
        }
        if (ids.isEmpty()) return null
        val arr = JSONArray()
        ids.forEach { topicId ->
            val topic = dao.getTopic(topicId) ?: return@forEach
            val past = dao.recentTopicSituations(topicId, situation.id, 8)
            val pastArr = JSONArray()
            past.forEach { row ->
                pastArr.put(
                    JSONObject()
                        .put(
                            "date",
                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                .format(java.util.Date(row.createdAt))
                        )
                        .put("text", row.summary.ifBlank { PsychLogic.shortStory(row.text, 500) })
                )
            }
            arr.put(
                JSONObject()
                    .put("name", topic.name)
                    .put("summary", topic.summaryText)
                    .put("past", pastArr)
            )
        }
        return if (arr.length() == 0) null else arr
    }

    suspend fun situationsInRange(from: Long, to: Long) = dao.situationsInRange(from, to)

    suspend fun usageToday(offsetMinutes: Int): Int {
        val from = PsychLogic.startOfTodayUtc(offsetMinutes)
        return dao.usageSince(from)
    }

    suspend fun recordUsage(kind: String, viaVoice: Boolean) {
        dao.insertUsage(PsychAiUsage(requestType = kind, viaVoice = viaVoice))
    }

    suspend fun cached(key: String): PsychAiCache? {
        val row = dao.getCache(key) ?: return null
        if (row.lockUntil > System.currentTimeMillis() && row.responseText.isBlank()) return null
        return row.takeIf { it.responseText.isNotBlank() }
    }

    suspend fun putCache(key: String, kind: String, text: String, prompt: String = "") {
        dao.upsertCache(
            PsychAiCache(
                cacheKey = key,
                requestType = kind,
                responseText = text,
                promptText = prompt
            )
        )
    }
}
