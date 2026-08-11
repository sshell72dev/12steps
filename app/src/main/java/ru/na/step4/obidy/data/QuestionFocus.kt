package ru.na.step4.obidy.data

/** Keys for per-field voice assistant focus on a situation. */
object QuestionFocus {
    const val TITLE = "title"
    const val WHAT = "what"
    const val FELT = "felt"
    const val DID = "did"

    fun q(number: Int) = "q$number"

    fun titleOf(key: String): String = when (key) {
        TITLE -> RuLabel.situationTitle
        WHAT -> InventoryStructure.WHAT_TITLE
        FELT -> InventoryStructure.FELT_TITLE
        DID -> InventoryStructure.DID_TITLE
        else -> {
            val n = key.removePrefix("q").toIntOrNull()
            InventoryStructure.questions.firstOrNull { it.number == n }?.title
                ?: key
        }
    }

    fun hintOf(key: String): String = when (key) {
        TITLE -> RuLabel.situationTitleHint
        WHAT -> InventoryStructure.WHAT_HINT
        FELT -> InventoryStructure.FELT_HINT
        DID -> InventoryStructure.DID_HINT
        else -> {
            val n = key.removePrefix("q").toIntOrNull()
            InventoryStructure.questions.firstOrNull { it.number == n }?.hint.orEmpty()
        }
    }

    /** Avoid depending on Ru from data layer for title/hint of situation name — thin bridge. */
    private object RuLabel {
        const val situationTitle = "\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435\u0020\u0441\u0438\u0442\u0443\u0430\u0446\u0438\u0438"
        const val situationTitleHint = "\u041a\u0440\u0430\u0442\u043a\u043e\u0020\u043e\u043f\u0438\u0448\u0438\u0442\u0435\u0020\u0441\u0438\u0442\u0443\u0430\u0446\u0438\u044e"
    }

    fun buildSituationAnswersText(
        target: String,
        situation: Situation,
        typeNames: List<String> = emptyList()
    ): String {
        val lines = mutableListOf<String>()
        if (target.isNotBlank()) lines += "\u041a\u043e\u043c\u0443/\u0447\u0435\u043c\u0443: $target"
        if (typeNames.isNotEmpty()) {
            lines += "\u0422\u0438\u043f\u044b: ${typeNames.joinToString(", ")}"
        }
        fun add(label: String, value: String) {
            if (value.isNotBlank()) lines += "$label: $value"
        }
        add(titleOf(TITLE), situation.title)
        add(InventoryStructure.WHAT_TITLE, situation.whatHappened)
        add(InventoryStructure.FELT_TITLE, situation.iFelt)
        add(InventoryStructure.DID_TITLE, situation.iDid)
        InventoryStructure.questions.forEach { q ->
            add(q.title, situation.answerFor(q.number))
        }
        return if (lines.isEmpty()) {
            "(\u043f\u043e\u043a\u0430 \u043d\u0435\u0442 \u0437\u0430\u043f\u043e\u043b\u043d\u0435\u043d\u043d\u044b\u0445 \u043e\u0442\u0432\u0435\u0442\u043e\u0432)"
        } else {
            lines.joinToString("\n")
        }
    }

    fun currentAnswer(situation: Situation, key: String): String = when (key) {
        TITLE -> situation.title
        WHAT -> situation.whatHappened
        FELT -> situation.iFelt
        DID -> situation.iDid
        else -> {
            val n = key.removePrefix("q").toIntOrNull() ?: return ""
            situation.answerFor(n)
        }
    }
}
