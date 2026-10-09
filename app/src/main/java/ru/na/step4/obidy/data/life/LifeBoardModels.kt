package ru.na.step4.obidy.data.life

data class LifeItem(
    val id: String,
    val kind: String,
    val title: String,
    val body: String = "",
    val status: String = LifeStatus.IN_PROGRESS,
    val dueAt: Long? = null,
    /** У события задано время: иначе оно стоит в дне без привязки к часам. */
    val timeSet: Boolean = false,
    /** Звонить в момент события: будильник и уведомление телефона. */
    val callOn: Boolean = false,
    /** Как часто повторять звонок: один раз, ежедневно, раз в неделю, раз в месяц. */
    val repeat: String = LifeRepeat.NONE,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val sourceId: String = ""
)

object LifeKind {
    const val GOAL = "goal"
    const val IDEA = "idea"
    const val EVENT = "event"
    const val NOTE = "note"

    val all = listOf(GOAL, IDEA, EVENT, NOTE)

    fun normalize(value: String?): String {
        val v = value.orEmpty().trim().lowercase()
        return if (v in all) v else NOTE
    }
}

object LifeStatus {
    const val IN_PROGRESS = "in_progress"
    const val DONE = "done"

    val all = listOf(IN_PROGRESS, DONE)

    fun label(status: String): String = when (status) {
        DONE -> LifeBoardRu.statusDone
        else -> LifeBoardRu.statusInProgress
    }

    fun normalize(value: String?): String {
        val v = value.orEmpty().trim().lowercase()
        return if (v in all) v else IN_PROGRESS
    }
}

/** Как часто звонит событие: один раз или с повтором. */
object LifeRepeat {
    const val NONE = "none"
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"

    val all = listOf(NONE, DAILY, WEEKLY, MONTHLY)

    /** Варианты, которые показываются кнопками рядом со «Звонком». */
    val repeating = listOf(DAILY, WEEKLY, MONTHLY)

    fun normalize(value: String?): String {
        val v = value.orEmpty().trim().lowercase()
        return if (v in all) v else NONE
    }

    /** Надпись на кнопке повторения. */
    fun optionLabel(value: String): String = when (normalize(value)) {
        DAILY -> LifeBoardRu.repeatDaily
        WEEKLY -> LifeBoardRu.repeatWeekly
        MONTHLY -> LifeBoardRu.repeatMonthly
        else -> ""
    }

    /** Короткая надпись для просмотра события: «ежедневно», «раз в неделю». */
    fun everyLabel(value: String): String = when (normalize(value)) {
        DAILY -> LifeBoardRu.repeatEveryDay
        WEEKLY -> LifeBoardRu.repeatEveryWeek
        MONTHLY -> LifeBoardRu.repeatEveryMonth
        else -> ""
    }

    /**
     * Следующее срабатывание строго после [from].
     * Для «без повтора» возвращается исходное время — оно могло уже пройти.
     */
    fun next(value: String, time: Long, from: Long): Long = when (normalize(value)) {
        DAILY -> nextFixed(time, from, DAY_MS)
        WEEKLY -> nextFixed(time, from, 7 * DAY_MS)
        MONTHLY -> nextMonth(time, from)
        else -> time
    }

    private fun nextFixed(time: Long, from: Long, step: Long): Long {
        if (time > from) return time
        val steps = (from - time) / step + 1
        return time + steps * step
    }

    private fun nextMonth(time: Long, from: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = time
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        var guard = 0
        // Число сохраняем: 31-е в коротком месяце станет его последним днём,
        // а в следующем длинном вернётся на 31-е.
        while (calendar.timeInMillis <= from && guard < 240) {
            calendar.add(java.util.Calendar.MONTH, 1)
            val last = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            calendar.set(java.util.Calendar.DAY_OF_MONTH, day.coerceAtMost(last))
            guard += 1
        }
        return calendar.timeInMillis
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
