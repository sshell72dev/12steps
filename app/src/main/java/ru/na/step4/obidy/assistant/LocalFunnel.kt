package ru.na.step4.obidy.assistant

import ru.na.step4.obidy.data.InventoryStructure

enum class FunnelStep(val key: String) {
    INTENT("intent"),
    TARGET("a_target"),
    TYPE("type"),
    WHAT("b_what"),
    FELT("b_felt"),
    DID("b_did"),
    Q1("q1"), Q2("q2"), Q3("q3"), Q4("q4"),
    Q5("q5"), Q6("q6"), Q7("q7"), Q8("q8"),
    Q9("q9"), Q10("q10"), Q11("q11"), Q12("q12"), Q13("q13"),
    CLOSE("close");

    fun next(): FunnelStep = entries[minOf(ordinal + 1, entries.lastIndex)]

    val questionNumber: Int?
        get() = key.removePrefix("q").toIntOrNull()
}

data class ChatTurn(
    val role: String,
    val content: String,
    val at: Long = System.currentTimeMillis()
)

data class DialogSession(
    val turns: List<ChatTurn> = emptyList(),
    val funnelStep: FunnelStep = FunnelStep.INTENT,
    val draftTarget: String = "",
    val draftType: String = "",
    val draftSituationType: String = "",
    val draftWhat: String = "",
    val draftFelt: String = "",
    val draftDid: String = "",
    val draftAnswers: Map<Int, String> = emptyMap(),
    val resentmentId: Long? = null
) {
    val hasPriorDialog: Boolean get() = turns.isNotEmpty()

    fun historyText(limit: Int = 24): String {
        if (turns.isEmpty()) return "(empty)"
        return turns.takeLast(limit).joinToString("\n") { "${it.role}: ${it.content}" }
    }

    fun funnelSummary(): String = buildString {
        append("step=").append(funnelStep.key)
        if (draftTarget.isNotBlank()) append("; target=").append(draftTarget)
        if (draftType.isNotBlank()) append("; type=").append(draftType)
        if (draftSituationType.isNotBlank()) append("; type=").append(draftSituationType)
        if (draftWhat.isNotBlank()) append("; what=").append(draftWhat.take(80))
        val filledQ = draftAnswers.count { it.value.isNotBlank() }
        append("; q_filled=").append(filledQ)
    }
}

object LocalFunnel {
    fun reply(session: DialogSession, userText: String): Pair<DialogSession, String> {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return session to promptFor(session.funnelStep)

        if (looksLikeJailbreak(trimmed)) {
            val msg = "\u042f\u0020\u043e\u0441\u0442\u0430\u044e\u0441\u044c\u0020\u0432\u0020\u0440\u0430\u043c\u043a\u0430\u0445\u0020\u0438\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u044f\u0020\u043e\u0431\u0438\u0434\u002e\u0020" +
                promptFor(session.funnelStep)
            return append(session, trimmed, msg) to msg
        }
        if (looksLikeCrisis(trimmed)) {
            val msg =
                "\u041f\u043e\u0436\u0430\u043b\u0443\u0439\u0441\u0442\u0430\u002c\u0020\u043e\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044c\u0020\u0441\u0435\u0439\u0447\u0430\u0441\u0020\u043a\u0020\u0431\u043b\u0438\u0437\u043a\u0438\u043c\u002c\u0020\u0441\u043f\u043e\u043d\u0441\u043e\u0440\u0443\u0020\u0438\u043b\u0438\u0020\u0432\u0020\u044d\u043a\u0441\u0442\u0440\u0435\u043d\u043d\u044b\u0435\u0020\u0441\u043b\u0443\u0436\u0431\u044b\u002e"
            return append(session, trimmed, msg) to msg
        }

        return when (val step = session.funnelStep) {
            FunnelStep.INTENT -> {
                val next = session.copy(funnelStep = FunnelStep.TARGET)
                val msg = "\u0425\u043e\u0440\u043e\u0448\u043e\u002e\u0020" + promptFor(FunnelStep.TARGET)
                append(next, trimmed, msg) to msg
            }
            FunnelStep.TARGET -> advance(session, trimmed, { copy(draftTarget = trimmed) }, FunnelStep.TYPE)
            FunnelStep.TYPE -> advance(session, trimmed, { copy(draftSituationType = trimmed) }, FunnelStep.WHAT)
            FunnelStep.WHAT -> advance(session, trimmed, { copy(draftWhat = trimmed) }, FunnelStep.FELT)
            FunnelStep.FELT -> advance(session, trimmed, { copy(draftFelt = trimmed) }, FunnelStep.DID)
            FunnelStep.DID -> advance(session, trimmed, { copy(draftDid = trimmed) }, FunnelStep.Q1)
            FunnelStep.Q1, FunnelStep.Q2, FunnelStep.Q3, FunnelStep.Q4,
            FunnelStep.Q5, FunnelStep.Q6, FunnelStep.Q7, FunnelStep.Q8,
            FunnelStep.Q9, FunnelStep.Q10, FunnelStep.Q11, FunnelStep.Q12, FunnelStep.Q13 -> {
                val num = step.questionNumber ?: 1
                val updated = session.copy(
                    draftAnswers = session.draftAnswers + (num to trimmed),
                    funnelStep = step.next()
                )
                val msg = "\u041f\u043e\u043d\u044f\u043b\u0430\u002e\u0020" + promptFor(updated.funnelStep)
                append(updated, trimmed, msg) to msg
            }
            FunnelStep.CLOSE -> {
                val msg =
                    "\u0417\u0430\u043f\u0438\u0448\u0438\u0442\u0435\u0020\u043e\u0442\u0432\u0435\u0442\u044b\u0020\u0432\u0020\u043a\u0430\u0440\u0442\u043e\u0447\u043a\u0443\u0020\u0438\u0020\u0440\u0430\u0437\u0431\u0435\u0440\u0438\u0442\u0435\u0020\u0441\u043e\u0020\u0441\u043f\u043e\u043d\u0441\u043e\u0440\u043e\u043c\u002e\u0020\u041d\u0443\u0436\u043d\u0430\u0020\u0435\u0449\u0451\u0020\u043e\u0434\u043d\u0430\u0020\u043e\u0431\u0438\u0434\u0430\u003f"
                val lower = trimmed.lowercase()
                val next = if (lower.contains("\u0434\u0430") || lower.contains("\u0435\u0449")) {
                    session.copy(
                        funnelStep = FunnelStep.TARGET,
                        draftTarget = "",
                        draftSituationType = "",
                        draftWhat = "",
                        draftFelt = "",
                        draftDid = "",
                        draftAnswers = emptyMap()
                    )
                } else session
                append(next, trimmed, msg) to msg
            }
        }
    }

    fun opening(session: DialogSession): String {
        return if (session.hasPriorDialog) {
            AssistantBrief.CONTINUE_MESSAGE + " " + promptFor(session.funnelStep)
        } else {
            AssistantBrief.FIRST_MESSAGE
        }
    }

    fun promptFor(step: FunnelStep): String = when (step) {
        FunnelStep.INTENT ->
            "\u0425\u043e\u0442\u0438\u0442\u0435\u0020\u043d\u0430\u0447\u0430\u0442\u044c\u0020\u043d\u043e\u0432\u0443\u044e\u0020\u043e\u0431\u0438\u0434\u0443\u0020\u0438\u043b\u0438\u0020\u043f\u0440\u043e\u0434\u043e\u043b\u0436\u0438\u0442\u044c\u003f"
        FunnelStep.TARGET -> InventoryStructure.TARGET_TITLE
        FunnelStep.TYPE -> InventoryStructure.TYPE_SECTION
        FunnelStep.WHAT -> InventoryStructure.WHAT_TITLE
        FunnelStep.FELT -> InventoryStructure.FELT_TITLE
        FunnelStep.DID -> InventoryStructure.DID_TITLE
        FunnelStep.CLOSE ->
            "\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u0435\u0020\u0432\u0020\u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u0435\u0020\u0438\u0020\u0440\u0430\u0437\u0431\u0435\u0440\u0438\u0442\u0435\u0020\u0441\u043e\u0020\u0441\u043f\u043e\u043d\u0441\u043e\u0440\u043e\u043c\u002e"
        else -> {
            val n = step.questionNumber
            if (n != null) AssistantBrief.questionPrompt(n) else promptFor(FunnelStep.CLOSE)
        }
    }

    private fun advance(
        session: DialogSession,
        user: String,
        draft: DialogSession.() -> DialogSession,
        next: FunnelStep
    ): Pair<DialogSession, String> {
        val updated = session.draft().copy(funnelStep = next)
        val msg = "\u041f\u043e\u043d\u044f\u043b\u0430\u002e\u0020" + promptFor(next)
        return append(updated, user, msg) to msg
    }

    private fun append(session: DialogSession, user: String, assistant: String): DialogSession {
        return session.copy(
            turns = session.turns + ChatTurn("user", user) + ChatTurn("assistant", assistant)
        )
    }

    private fun looksLikeJailbreak(text: String): Boolean {
        val t = text.lowercase()
        return listOf("системный промпт", "system prompt", "игнорируй", "ignore previous")
            .any { t.contains(it) }
    }

    private fun looksLikeCrisis(text: String): Boolean {
        val t = text.lowercase()
        return listOf("суицид", "убить себя", "не хочу жить", "покончить")
            .any { t.contains(it) }
    }
}
