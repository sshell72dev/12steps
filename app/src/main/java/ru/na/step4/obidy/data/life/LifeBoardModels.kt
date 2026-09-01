package ru.na.step4.obidy.data.life

data class LifeItem(
    val id: String,
    val kind: String,
    val title: String,
    val body: String = "",
    val status: String = LifeStatus.IN_PROGRESS,
    val dueAt: Long? = null,
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
