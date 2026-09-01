package ru.na.step4.obidy.data.support

import android.content.Context

data class SupportListResult(
    val tickets: List<SupportTicket> = emptyList(),
    val topicCounts: Map<String, Int> = emptyMap()
)

data class SupportMessage(
    val id: Long = 0,
    val author: String = "",
    val body: String = "",
    val createdAt: String = "",
    val editedAt: String = "",
    val edited: Boolean = false,
    val adminRead: Boolean = true
) {
    val fromAdmin: Boolean get() = author == "admin"
    val fromSystem: Boolean get() = author == "system"
    val fromUser: Boolean get() = author == "user"
}

data class SupportTicket(
    val id: Long = 0,
    val userId: String = "",
    val userName: String = "",
    val screen: String = "",
    val screenRoute: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val adminRead: Boolean = true,
    val userRead: Boolean = true,
    val status: String = SupportStatus.NEW,
    val statusLabel: String = SupportStatus.label(SupportStatus.NEW),
    val belonging: String = SupportBelonging.SCREEN,
    val belongingLabel: String = SupportBelonging.label(SupportBelonging.SCREEN),
    val kind: String = SupportKind.BUG,
    val kindLabel: String = SupportKind.label(SupportKind.BUG),
    val adminSource: String = "",
    val adminSourceLabel: String = "",
    val preview: String = "",
    val messages: List<SupportMessage> = emptyList()
)

object SupportStatus {
    const val NEW = "new"
    const val IN_PROGRESS = "in_progress"
    const val DONE = "done"

    val all = listOf(NEW, IN_PROGRESS, DONE)

    fun label(status: String): String = when (status) {
        NEW -> SupportRu.statusNew
        IN_PROGRESS -> SupportRu.statusInProgress
        DONE -> SupportRu.statusDone
        else -> SupportRu.statusNew
    }

    fun normalize(status: String?): String {
        val value = status.orEmpty().trim().lowercase()
        return if (value in all) value else NEW
    }
}

object SupportKind {
    const val BUG = "bug"
    const val IDEA = "idea"

    val all = listOf(BUG, IDEA)

    fun label(kind: String): String = when (kind) {
        IDEA -> SupportRu.kindIdea
        else -> SupportRu.kindBug
    }

    fun normalize(value: String?): String {
        val v = value.orEmpty().trim().lowercase()
        return if (v in all) v else BUG
    }
}

object SupportBelonging {
    const val SCREEN = "screen"
    const val GENERAL = "general"
    const val FAMILY = "family"
    const val REPORT = "report"
    const val IDEA_WINDOW = "idea_window"
    const val COVER = "cover"
    const val LIFE_IDEA = "life_idea"
    const val LIFE_NOTE = "life_note"
    const val LIFE_CALENDAR = "life_calendar"

    val all = listOf(
        SCREEN, GENERAL, FAMILY, REPORT, IDEA_WINDOW, COVER,
        LIFE_IDEA, LIFE_NOTE, LIFE_CALENDAR
    )

    fun choosableFor(kind: String): List<String> = when (SupportKind.normalize(kind)) {
        SupportKind.IDEA -> listOf(SCREEN, COVER, GENERAL, IDEA_WINDOW)
        else -> listOf(SCREEN, COVER, GENERAL, REPORT)
    }

    fun label(belonging: String): String = when (belonging) {
        GENERAL -> SupportRu.belongingGeneral
        FAMILY -> SupportRu.belongingFamily
        REPORT -> SupportRu.belongingReport
        IDEA_WINDOW -> SupportRu.belongingIdea
        COVER -> SupportRu.belongingCover
        LIFE_IDEA -> SupportRu.topicIdea
        LIFE_NOTE -> SupportRu.topicNotes
        LIFE_CALENDAR -> SupportRu.topicCalendar
        else -> SupportRu.belongingScreen
    }

    fun normalize(value: String?): String {
        val v = value.orEmpty().trim().lowercase()
        return if (v in all) v else SCREEN
    }

    fun resolve(belonging: String, route: String?, screenTitle: String): Pair<String, String> {
        return when (normalize(belonging)) {
            GENERAL -> SupportRu.belongingGeneral to "general"
            REPORT -> SupportRu.report to "support/report"
            IDEA_WINDOW -> SupportRu.ideaTitle to "support/idea"
            COVER -> SupportRu.belongingCover to "cover"
            LIFE_IDEA -> SupportRu.topicIdeaScreen to "life/idea"
            LIFE_NOTE -> SupportRu.topicNotes to "life/note"
            LIFE_CALENDAR -> SupportRu.topicCalendar to "life/event"
            FAMILY -> {
                val key = SupportScreens.familyKey(route)
                "${SupportScreens.familyTitle(key)} (все экраны)" to "family:$key"
            }
            else -> screenTitle to (route.orEmpty().ifBlank { "screen" })
        }
    }
}

object SupportTopic {
    const val IDEA = SupportBelonging.LIFE_IDEA
    const val NOTES = SupportBelonging.LIFE_NOTE
    const val CALENDAR = SupportBelonging.LIFE_CALENDAR

    val all = listOf(IDEA, NOTES, CALENDAR)

    fun isTopic(value: String?): Boolean = normalize(value) != null

    fun normalize(value: String?): String? {
        val v = value.orEmpty().trim().lowercase()
        return if (v in all) v else null
    }

    fun fromTicket(ticket: SupportTicket): String? {
        normalize(ticket.belonging)?.let { return it }
        val route = ticket.screenRoute.trim().lowercase()
        return when {
            route.startsWith("life/idea") -> IDEA
            route.startsWith("life/note") -> NOTES
            route.startsWith("life/event") || route.startsWith("life/calendar") -> CALENDAR
            else -> null
        }
    }

    fun merge(
        local: Map<String, Int>,
        server: Map<String, Int>,
        tickets: List<SupportTicket>
    ): Map<String, Int> {
        val fromTickets = tickets.mapNotNull { fromTicket(it) }.groupingBy { it }.eachCount()
        return all.associateWith { key ->
            maxOf(local[key] ?: 0, server[key] ?: 0, fromTickets[key] ?: 0)
        }
    }

    fun sorted(counts: Map<String, Int>): List<String> =
        all.sortedWith(
            compareByDescending<String> { counts[it] ?: 0 }
                .thenBy { all.indexOf(it) }
        )
}

class SupportTopicStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun counts(): Map<String, Int> =
        SupportTopic.all.associateWith { prefs.getInt(it, 0) }

    fun bump(topic: String) {
        val key = SupportTopic.normalize(topic) ?: return
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    companion object {
        private const val PREFS = "support_topic_freq"
    }
}
