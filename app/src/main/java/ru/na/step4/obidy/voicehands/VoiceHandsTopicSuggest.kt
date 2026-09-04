package ru.na.step4.obidy.voicehands

import ru.na.step4.obidy.data.psych.PsychTopic

/** Pending topic choice for the voice flow (existing id or a new name to create). */
data class VoiceTopicChoice(
    val topicId: Long? = null,
    val createName: String? = null,
    val displayName: String
) {
    val isEmpty: Boolean get() = topicId == null && createName.isNullOrBlank()
}

object VoiceHandsTopicSuggest {
    private val STOP = setOf(
        "и", "в", "во", "на", "по", "с", "со", "к", "ко", "у", "о", "об", "обо", "от", "до",
        "из", "за", "для", "при", "про", "без", "над", "под", "это", "эта", "этот", "эти",
        "я", "мне", "меня", "мой", "моя", "мое", "мы", "наш", "он", "она", "они", "ты",
        "что", "как", "когда", "где", "если", "или", "но", "а", "же", "ли", "бы", "не",
        "ни", "уже", "ещё", "еще", "очень", "сейчас", "сегодня", "вчера", "завтра",
        "был", "была", "было", "были", "есть", "будет", "тут", "там", "так", "то", "все",
        "всё", "просто", "типа", "короче", "ситуация", "проблема"
    )

    fun suggest(situation: String, topics: List<PsychTopic>): VoiceTopicChoice {
        val sitTokens = tokens(situation)
        val best = topics.mapNotNull { topic ->
            val score = scoreTopic(sitTokens, topic)
            if (score <= 0) null else topic to score
        }.maxByOrNull { it.second }
        if (best != null && best.second >= 2) {
            return VoiceTopicChoice(topicId = best.first.id, displayName = best.first.name)
        }
        val proposed = proposeName(situation)
        return if (proposed.isNotBlank()) {
            VoiceTopicChoice(createName = proposed, displayName = proposed)
        } else {
            VoiceTopicChoice(displayName = "")
        }
    }

    fun matchTopic(spoken: String, topics: List<PsychTopic>): PsychTopic? {
        val n = VoiceHandsPhrases.normalize(spoken)
        if (n.isBlank() || topics.isEmpty()) return null
        topics.firstOrNull { VoiceHandsPhrases.normalize(it.name) == n }?.let { return it }
        val contained = topics.filter { topic ->
            val name = VoiceHandsPhrases.normalize(topic.name)
            name.length >= 3 && (n.contains(name) || name.contains(n))
        }
        return contained.maxByOrNull { VoiceHandsPhrases.normalize(it.name).length }
    }

    fun stripTopicPrefix(spoken: String): String {
        var n = VoiceHandsPhrases.normalize(spoken)
        for (prefix in listOf(
            "тема",
            "выбрать тему",
            "выбираю тему",
            "выбираю",
            "выберу тему",
            "выберу",
            "назову тему",
            "назови тему",
            "новая тема"
        )) {
            if (n == prefix) return ""
            if (n.startsWith("$prefix ")) {
                n = n.removePrefix(prefix).trim()
            }
        }
        return n
    }

    private fun scoreTopic(sitTokens: Set<String>, topic: PsychTopic): Int {
        val nameTokens = tokens(topic.name)
        val summaryTokens = tokens(topic.summaryText)
        var score = 0
        for (t in nameTokens) {
            if (t in sitTokens) score += if (t.length >= 5) 3 else 2
        }
        for (t in summaryTokens) {
            if (t in sitTokens) score += 1
        }
        val fullSit = sitTokens.joinToString(" ")
        val name = VoiceHandsPhrases.normalize(topic.name)
        if (name.length >= 4 && fullSit.contains(name)) score += 4
        return score
    }

    private fun proposeName(situation: String): String {
        val words = VoiceHandsPhrases.normalize(situation)
            .split(' ')
            .filter { it.length >= 3 && it !in STOP }
            .take(4)
        if (words.isEmpty()) return ""
        val raw = words.joinToString(" ")
        return raw.replaceFirstChar { it.uppercaseChar() }.take(40).trim()
    }

    private fun tokens(text: String): Set<String> =
        VoiceHandsPhrases.normalize(text)
            .split(' ')
            .filter { it.length >= 3 && it !in STOP }
            .toSet()
}
