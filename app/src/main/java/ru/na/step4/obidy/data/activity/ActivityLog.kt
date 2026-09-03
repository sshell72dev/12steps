package ru.na.step4.obidy.data.activity

import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ru.na.step4.obidy.data.support.SupportScreens

class ActivityLog(
    private val dao: ActivityDao,
    private val scope: CoroutineScope
) {
    private val openSpans = ConcurrentHashMap<String, Long>()

    fun instant(
        category: String,
        type: String,
        label: String,
        detail: String = "",
        sessionKey: String = ""
    ) {
        val now = System.currentTimeMillis()
        scope.launch {
            dao.insert(
                ActivityEvent(
                    category = category,
                    type = type,
                    label = label.trim().take(160),
                    detail = detail.trim().take(400),
                    startedAt = now,
                    endedAt = now,
                    sessionKey = sessionKey
                )
            )
        }
    }

    fun start(
        category: String,
        type: String,
        label: String,
        sessionKey: String,
        detail: String = ""
    ) {
        if (sessionKey.isBlank()) return
        val now = System.currentTimeMillis()
        scope.launch {
            val existing = dao.openByKey(sessionKey)
            if (existing != null) {
                openSpans[sessionKey] = existing.id
                return@launch
            }
            val id = dao.insert(
                ActivityEvent(
                    category = category,
                    type = type,
                    label = label.trim().take(160),
                    detail = detail.trim().take(400),
                    startedAt = now,
                    endedAt = null,
                    sessionKey = sessionKey
                )
            )
            openSpans[sessionKey] = id
        }
    }

    fun end(sessionKey: String, detail: String = "") {
        if (sessionKey.isBlank()) return
        val now = System.currentTimeMillis()
        scope.launch {
            val id = openSpans.remove(sessionKey)
                ?: dao.openByKey(sessionKey)?.id
                ?: return@launch
            dao.close(id, now, detail.trim().take(400))
        }
    }

    fun screenChanged(route: String?) {
        val key = KEY_SCREEN
        val now = System.currentTimeMillis()
        val title = SupportScreens.title(route)
        scope.launch {
            val prev = openSpans.remove(key) ?: dao.openByKey(key)?.id
            if (prev != null) dao.close(prev, now, "")
            if (route.isNullOrBlank()) return@launch
            val id = dao.insert(
                ActivityEvent(
                    category = ActivityCat.SCREEN,
                    type = ActivityType.SCREEN,
                    label = title,
                    detail = route,
                    startedAt = now,
                    endedAt = null,
                    sessionKey = key
                )
            )
            openSpans[key] = id
        }
    }

    fun appBackground() {
        val now = System.currentTimeMillis()
        scope.launch {
            val prev = openSpans.remove(KEY_SCREEN) ?: dao.openByKey(KEY_SCREEN)?.id
            if (prev != null) dao.close(prev, now, "")
            val listen = openSpans.remove(KEY_LISTEN) ?: dao.openByKey(KEY_LISTEN)?.id
            if (listen != null) dao.close(listen, now, "")
        }
    }

    fun speakingChanged(on: Boolean, preview: String) {
        val clip = preview.trim().replace('\n', ' ').take(120)
        if (on) {
            start(
                category = ActivityCat.LISTEN,
                type = ActivityType.LISTEN_START,
                label = clip.ifBlank { ActivityRu.category(ActivityCat.LISTEN) },
                sessionKey = KEY_LISTEN,
                detail = clip
            )
        } else {
            val now = System.currentTimeMillis()
            scope.launch {
                val id = openSpans.remove(KEY_LISTEN) ?: dao.openByKey(KEY_LISTEN)?.id
                if (id != null) {
                    dao.close(id, now, clip)
                    dao.insert(
                        ActivityEvent(
                            category = ActivityCat.LISTEN,
                            type = ActivityType.LISTEN_END,
                            label = clip.ifBlank { ActivityRu.type(ActivityType.LISTEN_END) },
                            detail = clip,
                            startedAt = now,
                            endedAt = now,
                            sessionKey = KEY_LISTEN
                        )
                    )
                }
            }
        }
    }

    fun observe(from: Long, until: Long): Flow<List<ActivityEvent>> =
        dao.observeRange(from, until)

    fun analysisStart(title: String, catalogId: String) {
        start(
            ActivityCat.ANALYSIS,
            ActivityType.START,
            title,
            sessionKey = "analysis-$catalogId",
            detail = catalogId
        )
    }

    fun analysisAnswer(title: String, catalogId: String, answered: Int) {
        instant(
            ActivityCat.ANALYSIS,
            ActivityType.ANSWER,
            title,
            detail = ActivityRu.questions + ": $answered",
            sessionKey = "analysis-$catalogId"
        )
    }

    fun analysisFinish(title: String, catalogId: String, answered: Int) {
        val detail = "${ActivityRu.questions}: $answered"
        end(sessionKey = "analysis-$catalogId", detail = detail)
        instant(
            ActivityCat.ANALYSIS,
            ActivityType.FINISH,
            title,
            detail = detail,
            sessionKey = "analysis-$catalogId"
        )
    }

    companion object {
        private const val KEY_SCREEN = "screen"
        private const val KEY_LISTEN = "listen"

        fun dayBounds(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
            val cal = Calendar.getInstance()
            cal.timeInMillis = now
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            return start to cal.timeInMillis
        }

        fun weekBounds(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
            val (start, _) = dayBounds(now)
            val cal = Calendar.getInstance()
            cal.timeInMillis = start
            val first = cal.firstDayOfWeek
            while (cal.get(Calendar.DAY_OF_WEEK) != first) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            val from = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 7)
            return from to cal.timeInMillis
        }

        fun monthBounds(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
            val cal = Calendar.getInstance()
            cal.timeInMillis = now
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val from = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            return from to cal.timeInMillis
        }
    }
}

data class ActivitySummary(
    val screenMs: Long,
    val listenMs: Long,
    val analysisDone: Int,
    val answers: Int,
    val journalSaves: Int,
    val psychSessions: Int,
    val aiCalls: Int,
    val insights: List<String>
)

fun List<ActivityEvent>.summarize(): ActivitySummary {
    val screenMs = filter { it.category == ActivityCat.SCREEN }.sumOf { it.durationMs }
    val listenMs = filter {
        it.category == ActivityCat.LISTEN && it.type == ActivityType.LISTEN_START
    }.sumOf { it.durationMs }
    val analysisDone = count {
        it.category == ActivityCat.ANALYSIS && it.type == ActivityType.FINISH
    }
    val answers = count {
        it.category == ActivityCat.ANALYSIS && it.type == ActivityType.ANSWER
    }
    val journalSaves = count {
        it.category == ActivityCat.JOURNAL && it.type == ActivityType.SAVE
    }
    val psychSessions = count {
        it.category == ActivityCat.PSYCH && it.type == ActivityType.FINISH
    }
    val aiCalls = count { it.type == ActivityType.AI || it.category == ActivityCat.AI }
    val insights = buildInsights(
        screenMs, listenMs, analysisDone, answers, journalSaves, psychSessions, aiCalls, isEmpty()
    )
    return ActivitySummary(
        screenMs, listenMs, analysisDone, answers, journalSaves, psychSessions, aiCalls, insights
    )
}

private fun buildInsights(
    screenMs: Long,
    listenMs: Long,
    analysisDone: Int,
    answers: Int,
    journalSaves: Int,
    psychSessions: Int,
    aiCalls: Int,
    empty: Boolean
): List<String> {
    if (empty) return listOf(ActivityRu.empty)
    val lines = mutableListOf<String>()
    lines.add(
        "В приложении вы провели ${ActivityRu.duration(screenMs)}."
    )
    if (analysisDone > 0) {
        lines.add(
            "Завершено самоанализов: $analysisDone. Ответов на вопросы: $answers."
        )
    } else if (answers > 0) {
        lines.add("Есть незавершённые ответы в самоанализе ($answers). Стоит довести разбор до конца.")
    }
    if (journalSaves > 0) {
        lines.add("Записей в дневнике: $journalSaves.")
    }
    if (psychSessions > 0) {
        lines.add("Сессий с электронным психологом: $psychSessions.")
    }
    if (aiCalls > 0) {
        lines.add("Обращений к ИИ: $aiCalls — разборы и рекомендации сохраняются в ленте.")
    }
    if (listenMs > 0L) {
        lines.add("Прослушивание вслух: ${ActivityRu.duration(listenMs)}.")
    }
    when {
        screenMs < 3 * 60_000L && analysisDone == 0 && journalSaves == 0 ->
            lines.add("Короткий заход: практики почти не было. Даже 10 минут самоанализа уже меняют день.")
        analysisDone > 0 && journalSaves > 0 ->
            lines.add("Самоанализ и дневник шли рядом — так материал закрепляется лучше.")
        listenMs > 5 * 60_000L ->
            lines.add("Вы слушали ответы дольше пяти минут: полезно возвращаться к своим словам, а не только писать.")
        analysisDone >= 3 ->
            lines.add("Плотная серия самоанализов. Имеет смысл отметить, что именно повторяется в ответах.")
    }
    return lines
}
