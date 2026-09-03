package ru.na.step4.obidy.voicehands

import ru.na.step4.obidy.data.i18n.I18n

object VoiceHandsRu {
    val title: String get() = I18n.t("voicehands.title", "Голосовой режим ожидания")
    val experiment: String get() = I18n.t("voicehands.experiment", "Эксперимент")
    val hint: String get() = I18n.t(
        "voicehands.hint",
        "Пока приложение открыто. В ожидании микрофон выключен — нажмите «Слушать» и скажите «Давай запишем». Существующие экраны не меняются. Выключите, если что-то пойдёт не так."
    )
    val commands: String get() = I18n.t(
        "voicehands.commands",
        "Фразы: «Давай запишем» → диктуйте → «готово». Дальше ответ на вопрос или «разобрать ситуацию» / «рекомендации по ситуации». После «готово. читать?» — «читай». Затем «вернись в режим ожидания» или следующая команда."
    )
    val standby: String get() = I18n.t("voicehands.standby", "Ожидание")
    val dictating: String get() = I18n.t("voicehands.dictating", "Диктуйте")
    val thinking: String get() = I18n.t("voicehands.thinking", "Думаю")
    val awaiting: String get() = I18n.t("voicehands.awaiting", "Жду ответ")
    val askRead: String get() = I18n.t("voicehands.askRead", "Готово. Читать?")
    val reading: String get() = I18n.t("voicehands.reading", "Читаю")
    val afterRead: String get() = I18n.t("voicehands.afterRead", "Что дальше")
    val opening: String get() = I18n.t("voicehands.opening", "Открываю психолога")
    val off: String get() = I18n.t("voicehands.off", "Выключен")
    val disable: String get() = I18n.t("voicehands.disable", "Выключить")
    val toStandby: String get() = I18n.t("voicehands.toStandby", "В ожидание")
    val listening: String get() = I18n.t("voicehands.listening", "Слушаю")
    val listenButton: String get() = I18n.t("voicehands.listenButton", "Слушать")
    val quickButton: String get() = I18n.t("voicehands.quickButton", "Голосовой помощник")
    val quiet: String get() = I18n.t("voicehands.quiet", "Микрофон выключен")
    val hintStandby: String get() = I18n.t(
        "voicehands.hintStandby",
        "Нажмите «Слушать», затем скажите: «Давай запишем»"
    )
    val hintDictating: String get() = I18n.t(
        "voicehands.hintDictating",
        "Диктуйте ситуацию. Команды: «готово» · «Вернись в режим ожидания»"
    )
    val hintAwaiting: String get() = I18n.t(
        "voicehands.hintAwaiting",
        "Ответьте на вопрос или: «разобрать ситуацию» · «рекомендации по ситуации» · «Вернись в режим ожидания»"
    )
    val hintAskRead: String get() = I18n.t(
        "voicehands.hintAskRead",
        "«читай» · «разобрать ситуацию» · «рекомендации по ситуации» · «Вернись в режим ожидания»"
    )
    val hintAfterRead: String get() = I18n.t(
        "voicehands.hintAfterRead",
        "«Вернись в режим ожидания» · «разобрать ситуацию» · «рекомендации по ситуации» · «Давай запишем»"
    )
    val hintThinking: String get() = I18n.t(
        "voicehands.hintThinking",
        "Жду ответ ИИ. Если зависло — «В ожидание» или «Выключить»."
    )
    val thinkTimeout: String get() = I18n.t(
        "voicehands.thinkTimeout",
        "Не дождался ответа. Можно «В ожидание» или другую команду."
    )
    val hintOpening: String get() = I18n.t("voicehands.hintOpening", "Открываю новую запись…")
    val hintReading: String get() = I18n.t("voicehands.hintReading", "Читаю ответ. Подождите.")
    val needMic: String get() = I18n.t("voicehands.needMic", "Нужен доступ к микрофону.")
    val noEngine: String get() = I18n.t(
        "voicehands.noEngine",
        "На телефоне нет распознавания речи. Эксперимент не запустится."
    )
    val emptyDictation: String get() = I18n.t("voicehands.emptyDictation", "Пока ничего не услышал. Диктуйте и скажите «готово».")
    val psychMissing: String get() = I18n.t("voicehands.psychMissing", "Не удалось открыть психолога. Скажите «Давай запишем» ещё раз.")

    const val SAY_DICTATE = "диктуй"
    const val SAY_THINKING = "думаю"
    const val SAY_READY_READ = "готово. читать?"
    const val SAY_READ_DONE = "прочитал, что дальше"
    const val SAY_TIMEOUT = "не дождался ответа"

    fun hintsFor(phase: VoiceHandsPhase): String = when (phase) {
        VoiceHandsPhase.Standby -> hintStandby
        VoiceHandsPhase.Dictating -> hintDictating
        VoiceHandsPhase.AwaitingReply -> hintAwaiting
        VoiceHandsPhase.AskRead -> hintAskRead
        VoiceHandsPhase.AfterRead -> hintAfterRead
        VoiceHandsPhase.ThinkingQuestion,
        VoiceHandsPhase.ThinkingResult -> hintThinking
        VoiceHandsPhase.Opening -> hintOpening
        VoiceHandsPhase.Reading -> hintReading
        VoiceHandsPhase.Off -> ""
    }

    fun canTapListen(phase: VoiceHandsPhase): Boolean = when (phase) {
        VoiceHandsPhase.Standby,
        VoiceHandsPhase.Dictating,
        VoiceHandsPhase.AwaitingReply,
        VoiceHandsPhase.AskRead,
        VoiceHandsPhase.AfterRead -> true
        else -> false
    }
}

internal enum class VoiceHandsCommand {
    Start,
    Done,
    Analyze,
    Recommend,
    Read,
    Standby
}

internal object VoiceHandsPhrases {
    fun normalize(raw: String): String =
        raw.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^a-zа-я0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun matchCommand(raw: String): VoiceHandsCommand? {
        val n = normalize(raw)
        if (n.isBlank()) return null
        return when {
            matches(n, START) -> VoiceHandsCommand.Start
            matches(n, STANDBY) -> VoiceHandsCommand.Standby
            matches(n, ANALYZE) -> VoiceHandsCommand.Analyze
            matches(n, RECOMMEND) -> VoiceHandsCommand.Recommend
            matches(n, READ) -> VoiceHandsCommand.Read
            matches(n, DONE) -> VoiceHandsCommand.Done
            else -> null
        }
    }

    fun stripTrailingDone(raw: String): Pair<String, Boolean> {
        val n = normalize(raw)
        if (n.isBlank()) return "" to false
        for (phrase in DONE) {
            if (n == phrase) return "" to true
            if (n.endsWith(" $phrase")) {
                val lowered = raw.lowercase().replace('ё', 'е')
                val at = lowered.lastIndexOf(phrase)
                val body = if (at >= 0) raw.substring(0, at).trim().trimEnd(',', '.', '!', '?', '—', '-') else {
                    n.dropLast(phrase.length).trim()
                }
                return body to true
            }
        }
        return raw.trim() to false
    }

    private fun matches(normalized: String, phrases: List<String>): Boolean {
        for (phrase in phrases) {
            if (normalized == phrase) return true
            if (normalized.startsWith("$phrase ")) return true
            if (normalized.endsWith(" $phrase")) return true
            if (phrase.length >= 10 && normalized.contains(phrase)) return true
        }
        return false
    }

    private val START = listOf(
        "давай запишем",
        "давайте запишем",
        "давай запишем ситуацию",
        "давайте запишем ситуацию"
    )
    private val DONE = listOf("готово", "все готово")
    private val ANALYZE = listOf(
        "разобрать ситуации",
        "разобрать ситуацию",
        "разбери ситуацию",
        "разбери ситуации",
        "разбор ситуации",
        "разбор"
    )
    private val RECOMMEND = listOf(
        "рекомендации по ситуации",
        "рекомендация по ситуации",
        "дай рекомендации",
        "рекомендации",
        "рекомендация"
    )
    private val READ = listOf("читай", "прочитай", "читай дальше")
    private val STANDBY = listOf(
        "вернись в режим ожидания",
        "вернитесь в режим ожидания",
        "режим ожидания",
        "вернись в ожидание",
        "в ожидание"
    )
}
