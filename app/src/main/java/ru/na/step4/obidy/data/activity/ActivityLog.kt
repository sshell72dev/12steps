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
            if (route.isNullOrBlank() || !route.startsWith("journal")) {
                val write = openSpans.remove(KEY_JOURNAL) ?: dao.openByKey(KEY_JOURNAL)?.id
                if (write != null) dao.close(write, now, "")
            }
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
            val write = openSpans.remove(KEY_JOURNAL) ?: dao.openByKey(KEY_JOURNAL)?.id
            if (write != null) dao.close(write, now, "")
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
        val key = "analysis-$catalogId"
        val detail = "${ActivityRu.questions}: $answered"
        scope.launch {
            val id = openSpans[key] ?: dao.openByKey(key)?.id ?: return@launch
            dao.updateDetail(id, detail)
        }
        instant(
            ActivityCat.ANALYSIS,
            ActivityType.ANSWER,
            title,
            detail = detail,
            sessionKey = key
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

    fun analysisLeave(catalogId: String, answered: Int, done: Boolean) {
        if (done) return
        end(
            sessionKey = "analysis-$catalogId",
            detail = "${ActivityRu.unfinished}: ${ActivityRu.questions}: $answered"
        )
    }

    fun aiBegin(label: String, key: String, detail: String = "") {
        start(ActivityCat.AI, ActivityType.AI, label, sessionKey = key, detail = detail)
    }

    fun aiDone(key: String, detail: String = "") {
        end(sessionKey = key, detail = detail)
    }

    fun inventoryStart(title: String, situationId: Long) {
        start(
            ActivityCat.INVENTORY,
            ActivityType.START,
            title.ifBlank { ActivityRu.category(ActivityCat.INVENTORY) },
            sessionKey = "situation-$situationId"
        )
    }

    fun inventoryEnd(situationId: Long, detail: String = "") {
        end(sessionKey = "situation-$situationId", detail = detail)
    }

    fun journalWriteStart(title: String) {
        start(
            ActivityCat.JOURNAL,
            ActivityType.START,
            title.ifBlank { ActivityRu.category(ActivityCat.JOURNAL) },
            sessionKey = KEY_JOURNAL
        )
    }

    fun journalWriteEnd(detail: String = "") {
        end(sessionKey = KEY_JOURNAL, detail = detail)
    }

    companion object {
        private const val KEY_SCREEN = "screen"
        private const val KEY_LISTEN = "listen"
        private const val KEY_JOURNAL = "journal-write"

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
    val analysisMs: Long,
    val psychMs: Long,
    val inventoryMs: Long,
    val journalMs: Long,
    val aiMs: Long,
    val analysisDone: Int,
    val answers: Int,
    val journalSaves: Int,
    val psychSessions: Int,
    val inventorySessions: Int,
    val aiCalls: Int,
    val analysisTitles: List<String>,
    val insights: List<String>
)

fun List<ActivityEvent>.summarize(): ActivitySummary {
    fun spanMs(cat: String, type: String = ActivityType.START) =
        filter { it.category == cat && it.type == type }.sumOf { it.durationMs }
    val screenMs = filter { it.category == ActivityCat.SCREEN }.sumOf { it.durationMs }
    val listenMs = filter {
        it.category == ActivityCat.LISTEN && it.type == ActivityType.LISTEN_START
    }.sumOf { it.durationMs }
    val analysisMs = spanMs(ActivityCat.ANALYSIS)
    val psychMs = spanMs(ActivityCat.PSYCH)
    val inventoryMs = spanMs(ActivityCat.INVENTORY)
    val journalMs = filter {
        it.category == ActivityCat.JOURNAL && it.type == ActivityType.START
    }.sumOf { it.durationMs }
    val aiMs = filter { it.category == ActivityCat.AI }.sumOf { it.durationMs }
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
    val inventorySessions = count {
        it.category == ActivityCat.INVENTORY && it.type == ActivityType.START
    }
    val aiCalls = count { it.type == ActivityType.AI || it.category == ActivityCat.AI }
    val analysisTitles = filter {
        it.category == ActivityCat.ANALYSIS && it.type == ActivityType.FINISH && it.label.isNotBlank()
    }.map { it.label }.distinct()
    val insights = buildInsights(this, screenMs, listenMs, analysisMs, analysisDone, answers, journalSaves, psychSessions, inventorySessions, aiCalls)
    return ActivitySummary(
        screenMs, listenMs, analysisMs, psychMs, inventoryMs, journalMs, aiMs,
        analysisDone, answers, journalSaves, psychSessions, inventorySessions, aiCalls,
        analysisTitles, insights
    )
}

fun List<ActivityEvent>.primaryTimeline(): List<ActivityEvent> {
    val spanKeys = filter { it.durationMs >= 1000L }.map { it.sessionKey }.filter { it.isNotBlank() }.toSet()
    return filter { event ->
        when {
            event.category == ActivityCat.SCREEN -> false
            event.type == ActivityType.ANSWER -> false
            event.type == ActivityType.LISTEN_END -> false
            event.type == ActivityType.FINISH && event.sessionKey in spanKeys && event.durationMs < 1000L -> false
            else -> true
        }
    }
}

private fun buildInsights(
    events: List<ActivityEvent>,
    screenMs: Long,
    listenMs: Long,
    analysisMs: Long,
    analysisDone: Int,
    answers: Int,
    journalSaves: Int,
    psychSessions: Int,
    inventorySessions: Int,
    aiCalls: Int
): List<String> {
    if (events.isEmpty()) return listOf(ActivityRu.empty)
    val lines = mutableListOf<String>()
    lines.add(ActivityRu.insightTotal.format(ActivityRu.duration(screenMs)))
    val finished = events.filter {
        it.category == ActivityCat.ANALYSIS && it.type == ActivityType.FINISH
    }
    if (finished.isNotEmpty()) {
        val names = finished.map { it.label }.filter { it.isNotBlank() }.distinct().joinToString(", ")
        val named = if (names.isNotBlank()) " ($names)" else ""
        lines.add(
            ActivityRu.insightAnalysis.format(
                analysisDone,
                answers,
                ActivityRu.duration(analysisMs)
            ) + named
        )
    } else if (answers > 0 || analysisMs > 0L) {
        lines.add(ActivityRu.insightUnfinished.format(answers, ActivityRu.duration(analysisMs)))
    }
    if (journalSaves > 0) {
        lines.add(ActivityRu.insightJournal.format(journalSaves))
    }
    if (psychSessions > 0) {
        lines.add(ActivityRu.insightPsych.format(psychSessions))
    }
    if (inventorySessions > 0) {
        lines.add(ActivityRu.insightInventory.format(inventorySessions))
    }
    if (aiCalls > 0) {
        val aiMs = events.filter { it.category == ActivityCat.AI }.sumOf { it.durationMs }
        val extra = if (aiMs >= 1000L) " (${ActivityRu.duration(aiMs)})" else ""
        lines.add(ActivityRu.insightAi.format(aiCalls) + extra)
    }
    if (listenMs > 0L) {
        val listens = events.count {
            it.category == ActivityCat.LISTEN && it.type == ActivityType.LISTEN_START
        }
        lines.add(ActivityRu.insightListen.format(listens, ActivityRu.duration(listenMs)))
    }
    when {
        screenMs < 3 * 60_000L && analysisDone == 0 && journalSaves == 0 && psychSessions == 0 ->
            lines.add(ActivityRu.insightShort)
        analysisDone > 0 && journalSaves > 0 ->
            lines.add(ActivityRu.insightCombo)
        listenMs > 5 * 60_000L ->
            lines.add(ActivityRu.insightListenLong)
        analysisDone >= 3 ->
            lines.add(ActivityRu.insightSeries)
        inventorySessions > 0 && aiCalls > 0 ->
            lines.add(ActivityRu.insightInventoryAi)
    }
    return lines
}
