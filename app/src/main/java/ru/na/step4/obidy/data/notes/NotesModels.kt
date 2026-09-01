package ru.na.step4.obidy.data.notes

enum class NoteMode {
    POPUP,
    COLLAPSED,
    EXPANDED;

    val api: String
        get() = when (this) {
            POPUP -> "popup"
            COLLAPSED -> "collapsed"
            EXPANDED -> "expanded"
        }

    companion object {
        fun fromApi(value: String?, fallback: NoteMode = COLLAPSED): NoteMode =
            when (value?.lowercase()) {
                "popup" -> POPUP
                "expanded" -> EXPANDED
                "collapsed" -> COLLAPSED
                else -> fallback
            }
    }
}

data class NoteOverride(
    val id: String,
    val title: String = "",
    val text: String = "",
    val mode: NoteMode = NoteMode.COLLAPSED,
    val showTitle: Boolean = false,
    val updatedAt: String = "",
    val dirty: Boolean = false
)

data class ResolvedNote(
    val id: String,
    val title: String,
    val text: String,
    val mode: NoteMode,
    val showTitle: Boolean = false
)

object NoteIds {
    fun journal(nodeId: Int) = "journal.$nodeId"
    const val JOURNAL_HUB_INTRO = "journal.hub.intro"
    const val JOURNAL_PICK_HINT = "journal.pick.hint"
    const val JOURNAL_PERSONALITY = "journal.personality.hint"
    const val ANALYSIS_INTRO = "analysis.intro"
    const val ANALYSIS_MINI_HINT = "analysis.mini.hint"
    const val ANALYSIS_SETTINGS = "analysis.settings.hint"
    fun analysisPreview(catalogId: String, index: Int) = "analysis.$catalogId.preview.$index"
    fun analysisPrayer(catalogId: String, title: String) =
        "analysis.$catalogId.prayer.${title.trim().lowercase().hashCode() and 0x7fffffff}"
    const val PSYCH_INTRO = "psych.intro"
    const val PSYCH_DISCLAIMER = "psych.disclaimer"
    const val INVENTORY_WORK = "inventory.work"
    const val INVENTORY_POINT_A = "inventory.point_a"
    const val INVENTORY_POINT_B = "inventory.point_b"
    const val INVENTORY_POINT_V = "inventory.point_v"
    const val INVENTORY_POINT_G = "inventory.point_g"
    const val INVENTORY_TARGET = "inventory.target"
    const val INVENTORY_SITUATION = "inventory.situation"
    const val INVENTORY_Q_SECTION = "inventory.q_section"
    const val INVENTORY_CATEGORY = "inventory.category"
    const val INVENTORY_TYPE = "inventory.type"
    const val INVENTORY_WHAT = "inventory.what"
    const val INVENTORY_FELT = "inventory.felt"
    const val INVENTORY_DID = "inventory.did"
    const val ASSISTANT = "inventory.assistant"
    fun inventoryQuestion(number: Int) = "inventory.q.$number"
}

fun defaultNoteMode(@Suppress("UNUSED_PARAMETER") text: String): NoteMode = NoteMode.POPUP
