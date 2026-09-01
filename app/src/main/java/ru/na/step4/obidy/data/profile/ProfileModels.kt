package ru.na.step4.obidy.data.profile

data class QuestionnaireQuestion(
    val id: String,
    val text: String,
    val options: List<String> = emptyList(),
    val hint: String = ""
)

data class ProblemOption(
    val key: String,
    val label: String
)

data class ProfileSnapshot(
    val name: String = "",
    val birthYear: String = "",
    val location: String = "",
    val aboutMe: String = "",
    val program: String = "",
    val problems: Set<String> = emptySet(),
    val personality: String = "",
    val personalityEnabled: Boolean = true,
    val personalityCollectEnabled: Boolean = false,
    val answers: Map<String, String> = emptyMap(),
    val skipped: Set<String> = emptySet(),
    val languageCode: String = "ru"
)

object ProfileProblems {
    val all = listOf(
        ProblemOption("drugs", "Наркотики"),
        ProblemOption("alcohol", "Алкоголь"),
        ProblemOption("gambling", "Игромания"),
        ProblemOption("depression", "Депрессия"),
        ProblemOption("family_conflicts", "Конфликты в семье"),
        ProblemOption("work_conflicts", "Конфликты на работе")
    )

    fun labels(keys: Set<String>): String? {
        if (keys.isEmpty()) return null
        return all.filter { it.key in keys }.joinToString(", ") { it.label }.ifBlank { null }
    }
}

object ProfileQuestionnaire {
    const val ID_NAME = "name"
    const val ID_PROGRAM = "section1:program_type"
    const val ID_GENDER = "section1:gender"
    const val ID_ADDICTION = "section2:addiction_type"
    const val ID_LAST_USE = "section2:last_use_date"
    const val ID_REASON = "section4:main_reason"
    const val ID_MOTIVATION = "section4:motivation_level"
    const val ID_BIRTH = "birth_year"
    const val ID_LOCATION = "location"
    const val ID_ABOUT = "about_me"

    val programs = listOf(
        "12 шагов Анонимных Наркоманов",
        "12 шагов Анонимных Алкоголиков",
        "Другая программа 12 шагов",
        "Не работаю по программе",
        "Православная Церковь",
        "Протестантская церковь",
        "Традиционная психология"
    )

    val questions = listOf(
        QuestionnaireQuestion(ID_NAME, "Как к вам обращаться?", hint = "Имя"),
        QuestionnaireQuestion(ID_PROGRAM, "По какой программе вы работаете?", options = programs),
        QuestionnaireQuestion(
            ID_GENDER,
            "Пол",
            options = listOf("Мужской", "Женский", "Другое", "Не указывать")
        ),
        QuestionnaireQuestion(
            ID_ADDICTION,
            "Основной вид зависимости",
            options = listOf(
                "Алкоголь",
                "Никотин",
                "Наркотики",
                "Игровая/гэмблинг",
                "Пищевая",
                "Интернет и соцсети",
                "Другое"
            )
        ),
        QuestionnaireQuestion(
            ID_LAST_USE,
            "Дата последнего употребления/срыва",
            hint = "Формат: ДД.ММ.ГГГГ или «сегодня», «вчера»"
        ),
        QuestionnaireQuestion(
            ID_REASON,
            "Основная причина, по которой хочу избавиться от зависимости",
            options = listOf("Здоровье", "Семья", "Карьера", "Закон", "Самоуважение", "Другое")
        ),
        QuestionnaireQuestion(
            ID_MOTIVATION,
            "Уровень мотивации к выздоровлению (по шкале от 1 до 10)",
            options = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        ),
        QuestionnaireQuestion(ID_BIRTH, "Год рождения", hint = "Например 1988"),
        QuestionnaireQuestion(ID_LOCATION, "Место", hint = "Город или регион"),
        QuestionnaireQuestion(ID_ABOUT, "О себе", hint = "Коротко, своими словами")
    )

    fun nextUnanswered(snapshot: ProfileSnapshot): QuestionnaireQuestion? {
        val programAnswered = snapshot.program.isNotBlank() ||
            snapshot.answers[ID_PROGRAM].orEmpty().isNotBlank()
        return questions.firstOrNull { q ->
            if (q.id in snapshot.skipped) return@firstOrNull false
            when (q.id) {
                ID_NAME -> snapshot.name.isBlank()
                ID_PROGRAM -> !programAnswered
                ID_BIRTH -> snapshot.birthYear.isBlank()
                ID_LOCATION -> snapshot.location.isBlank()
                ID_ABOUT -> snapshot.aboutMe.isBlank()
                else -> snapshot.answers[q.id].orEmpty().isBlank()
            }
        }
    }

    fun formatAnswers(snapshot: ProfileSnapshot): String? {
        val lines = buildList {
            if (snapshot.name.isNotBlank()) add("Имя: ${snapshot.name}")
            questions.forEach { q ->
                val value = when (q.id) {
                    ID_NAME -> return@forEach
                    ID_PROGRAM -> snapshot.program.ifBlank { snapshot.answers[q.id].orEmpty() }
                    ID_BIRTH -> snapshot.birthYear
                    ID_LOCATION -> snapshot.location
                    ID_ABOUT -> snapshot.aboutMe
                    else -> snapshot.answers[q.id].orEmpty()
                }
                if (value.isNotBlank()) add("${q.text}: $value")
            }
            ProfileProblems.labels(snapshot.problems)?.let { add("Обозначенные проблемы: $it") }
        }
        return lines.joinToString("\n").ifBlank { null }
    }
}

object ProfileRu {
    val title: String get() = ru.na.step4.obidy.data.i18n.I18n.t("profile.title", "Анкета и «Моя личность»")
    val eyebrow: String get() = ru.na.step4.obidy.data.i18n.I18n.t("profile.eyebrow", "Общий профиль")
    val homeBody: String get() = ru.na.step4.obidy.data.i18n.I18n.t(
        "profile.homeBody",
        "Имя, анкета и портрет — одни на все разделы. ИИ не спрашивает имя повторно, если оно уже есть."
    )
    val intro: String get() = ru.na.step4.obidy.data.i18n.I18n.t(
        "profile.intro",
        "Эта анкета общая для дневника, самоанализа и электронного психолога. Имя спрашивается один раз."
    )
    val sectionAnketa: String get() = ru.na.step4.obidy.data.i18n.I18n.t("profile.sectionAnketa", "Анкета")
    val sectionPersonality: String get() = ru.na.step4.obidy.data.i18n.I18n.t("profile.sectionPersonality", "Моя личность")
    val personalityHint: String get() = ru.na.step4.obidy.data.i18n.I18n.t(
        "profile.personalityHint",
        "Сжатый портрет для персонализации ИИ во всех разделах. Можно править вручную. " +
            "Если включено автообновление, ИИ дополняет портрет после анализа записей и ситуаций."
    )
    val personalityEmpty: String get() = ru.na.step4.obidy.data.i18n.I18n.t(
        "profile.personalityEmpty",
        "Пока не заполнено. Портрет появится после анализа записей или ситуаций."
    )
    val personalityOn: String get() = ru.na.step4.obidy.data.i18n.I18n.t(
        "profile.personalityOn",
        "Использовать в ИИ во всех разделах"
    )
    val personalityOff: String get() = ru.na.step4.obidy.data.i18n.I18n.t(
        "profile.personalityOff",
        "Не подмешивать в ИИ"
    )
    val collectOn: String get() = ru.na.step4.obidy.data.i18n.I18n.t(
        "profile.collectOn",
        "Автообновление портрета из ИИ (Premium)"
    )
    val collectOff: String get() = ru.na.step4.obidy.data.i18n.I18n.t(
        "profile.collectOff",
        "Автообновление портрета выключено"
    )
    val collectProNeeded: String get() = ru.na.step4.obidy.data.i18n.I18n.t(
        "profile.collectProNeeded",
        "Автообновление портрета доступно с Premium"
    )
    val saveAll: String get() = ru.na.step4.obidy.data.i18n.I18n.t("profile.saveAll", "Сохранить анкету")
    val saved: String get() = ru.na.step4.obidy.data.i18n.I18n.t("profile.saved", "Анкета сохранена")
    val name: String get() = ru.na.step4.obidy.data.i18n.I18n.t("profile.name", "Имя")
    val nameHint: String get() = ru.na.step4.obidy.data.i18n.I18n.t("profile.nameHint", "Как к вам обращаться")
    val skipName: String get() = ru.na.step4.obidy.data.i18n.I18n.t("profile.skipName", "Пропустить имя")
    val problems: String get() = ru.na.step4.obidy.data.i18n.I18n.t("profile.problems", "Обозначьте свою проблему")
    val problemsHint: String get() = ru.na.step4.obidy.data.i18n.I18n.t(
        "profile.problemsHint",
        "Можно выбрать несколько или пропустить."
    )
    val customProgram: String get() = ru.na.step4.obidy.data.i18n.I18n.t(
        "profile.customProgram",
        "Своё название программы"
    )
    val filledCount: String get() = ru.na.step4.obidy.data.i18n.I18n.t(
        "profile.filledCount",
        "Заполнено %1\$d из %2\$d"
    )
    val language: String get() = ru.na.step4.obidy.data.i18n.I18n.t("profile.language", "Язык интерфейса")
    val languageHint: String get() = ru.na.step4.obidy.data.i18n.I18n.t(
        "profile.languageHint",
        "Заголовки, подсказки и ответы ИИ будут на выбранном языке. Ваши записи не переводятся."
    )
    val languageSearch: String get() = ru.na.step4.obidy.data.i18n.I18n.t("profile.languageSearch", "Поиск языка")
    val languageOther: String get() = ru.na.step4.obidy.data.i18n.I18n.t(
        "profile.languageOther",
        "Другой код языка (например en, de, pt-BR)"
    )
    val languageApply: String get() = ru.na.step4.obidy.data.i18n.I18n.t("profile.languageApply", "Применить язык")
}
