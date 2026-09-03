package ru.na.step4.obidy.data

import ru.na.step4.obidy.data.i18n.I18n

data class InventoryQuestion(
    val number: Int,
    val title: String,
    val hint: String
)

object InventoryStructure {
    val POINT_A: String get() = I18n.t("inventory.POINT_A", "Пункт А")
    val POINT_B: String get() = I18n.t("inventory.POINT_B", "Пункт Б")
    val POINT_V: String get() = I18n.t("inventory.POINT_V", "Пункт В")
    val POINT_G: String get() = I18n.t("inventory.POINT_G", "Пункт Г")

    val INTRO_TITLE: String get() = I18n.t("inventory.INTRO_TITLE", "I. Обиды")
    val INTRO: String get() = I18n.t(
        "inventory.INTRO",
        "Наши обиды не давали нам покоя. Мы снова и снова проживали в своем сознании неприятные события из прошлого. Мы злились на то, что произошло, и вели подсчет всех своих обид. Мы сожалели о тех умных вещах, которых так и не сказали, и вынашивали планы расплаты за то, что было, или за то, чего могло и не быть. Мы были одержимы прошлым и будущим, и поэтому врали себе про настоящее. Теперь нам нужно написать об этих обидах, чтобы увидеть, какую роль мы сыграли в их появлении."
    )

    val POINT_A_BODY: String get() = I18n.t(
        "inventory.POINT_A_BODY",
        "Перечисли людей, организации и концепции, на которые ты обижен. Большинство из нас начинает с детства, но можно писать в любом порядке, главное, чтобы список был полным. Включи в него всех людей (родителей, друзей и подруг, врагов, самого себя и т. д.); организации и учреждения (тюрьмы, полицию, больницы, школы или институты и т. д.); а также концепции (религии, политику, предрассудки, социальные обычаи, Бога и т. д.), в отношении которых ты испытываешь чувство злости."
    )

    val POINT_B_BODY: String get() = I18n.t(
        "inventory.POINT_B_BODY",
        "Перечисли причину или причины каждой обиды. В каждой обиде мы изучаем причину нашей злости и то, как она повлияла на нас. Вот некоторые из вопросов, которые мы задаем себе, чтобы помочь себе определить наши чувства:"
    )

    val POINT_V_BODY: String get() = I18n.t(
        "inventory.POINT_V_BODY",
        "В отношении каждой обиды мы стараемся увидеть, в чем мы совершили ошибку, и какую роль сыграли в ситуации. Какой была в каждой ситуации наша реакция на свои чувства? Мы должны быть максимально честны, мы должны разобраться, какие дефекты характера сыграли в наших действиях свою роль. Вот некоторые из вопросов, которые мы себе задаем:"
    )

    val POINT_G_BODY: String get() = I18n.t(
        "inventory.POINT_G_BODY",
        "Ситуации, в которых мы уверены в своей правоте, требуют пристального внимания и обсуждения с нашим спонсором. Ответы на эти и другие вопросы применительно именно к нам самим помогают нам определить свои дефекты характера. Отвечать мы должны честно, не упуская ничего. Если где-то кто-то поступил с нами неправильно, мы должны осознать, что нам необходимо прекратить ожидания безупречности от других. Если мы рассчитываем когда-нибудь обрести мир в душе, мы должны научиться принимать других такими, какие они есть."
    )

    val TARGET_TITLE: String get() = I18n.t("inventory.TARGET_TITLE", "На кого или на что я обижен?")
    val TARGET_HINT: String get() = I18n.t("inventory.TARGET_HINT", "Человек, организация, учреждение или концепция")
    val TYPE_SECTION: String get() = I18n.t("inventory.TYPE_SECTION", "Типы ситуаций")
    val TYPE_SECTION_HINT: String get() = I18n.t(
        "inventory.TYPE_SECTION_HINT",
        "Одна обида может включать несколько типов ситуаций."
    )
    val SITUATION_SECTION: String get() = I18n.t("inventory.SITUATION_SECTION", "Ситуации")
    val SITUATION_SECTION_HINT: String get() = I18n.t(
        "inventory.SITUATION_SECTION_HINT",
        "Для каждой обиды опишите причину, чувства, свою роль и ответьте на вопросы пунктов Б и В."
    )
    val TYPE_CUSTOM: String get() = I18n.t("inventory.TYPE_CUSTOM", "Свой тип…")

    private val suggestedSituationTypesSrc = listOf(
        "Критика / унижение",
        "Предательство",
        "Игнор",
        "Контроль",
        "Невыполненные обещания",
        "Насилие / угроза",
        "Разочарование в ожиданиях"
    )

    val suggestedSituationTypes: List<String>
        get() = suggestedSituationTypesSrc.mapIndexed { i, label ->
            I18n.t("inventory.type.$i", label)
        }

    val WHAT_TITLE: String get() = I18n.t("inventory.WHAT_TITLE", "Причина или причины обиды")
    val WHAT_HINT: String get() = I18n.t("inventory.WHAT_HINT", "Почему я злюсь и как это на меня повлияло")
    val FELT_TITLE: String get() = I18n.t("inventory.FELT_TITLE", "Я чувствовал")
    val FELT_HINT: String get() = I18n.t(
        "inventory.FELT_HINT",
        "Какие чувства были тогда и какие возвращаются. Можно выбрать из таблицы чувств."
    )
    val feelingsTable: String get() = I18n.t("inventory.feelingsTable", "Таблица чувств")
    val DID_TITLE: String get() = I18n.t("inventory.DID_TITLE", "Я делал")
    val DID_HINT: String get() = I18n.t(
        "inventory.DID_HINT",
        "Как я реагировал: что говорил, делал или не делал"
    )
    val Q_SECTION: String get() = I18n.t("inventory.Q_SECTION", "Вопросы к обиде")
    val Q_SECTION_HINT: String get() = I18n.t(
        "inventory.Q_SECTION_HINT",
        "Пункт Б — вопросы 1–4. Пункт В — вопросы 5–12. Можно заполнять по частям."
    )

    private val questionsSrc = listOf(
        InventoryQuestion(1, "Была ли причиной моей обиды гордость?", "Гордость как причина злости."),
        InventoryQuestion(2, "Угрожало ли что-либо моей безопасности или моему благополучию?", "Безопасность и благополучие."),
        InventoryQuestion(3, "Были ли задеты или поставлены под угрозу личные отношения или сексуальные связи?", "Личные отношения или сексуальные связи."),
        InventoryQuestion(4, "Привели ли мои желания к конфликту с другими?", "Желания и конфликт с другими."),
        InventoryQuestion(5, "Где в основе моих действий были жадность или потребность подчинить?", "Жадность или потребность подчинить."),
        InventoryQuestion(6, "До каких крайностей я доходил в своих обидах?", "Крайности в обидах."),
        InventoryQuestion(7, "Каким образом я манипулировал другими, и зачем?", "Манипуляции и цель."),
        InventoryQuestion(8, "В чем был эгоизм моего поведения?", "Эгоизм поведения."),
        InventoryQuestion(9, "Думал ли я, что жизнь мне что-то должна?", "Ожидание, что жизнь мне должна."),
        InventoryQuestion(10, "Каким образом мои ожидания от других приводили к проблемам?", "Ожидания от других и проблемы."),
        InventoryQuestion(11, "Как в этой ситуации проявлялись гордыня и эго?", "Гордыня и эго."),
        InventoryQuestion(12, "Как меня мотивировал страх?", "Страх как мотив."),
        InventoryQuestion(13, "Какие чувства я не умел или не был готов проживать, и как я их избегал?", "Непрожитые чувства и способы избегания. Можно выбрать из таблицы чувств.")
    )

    val questions: List<InventoryQuestion>
        get() = questionsSrc.map { q ->
            InventoryQuestion(
                number = q.number,
                title = I18n.t("inventory.q.${q.number}.title", q.title),
                hint = I18n.t("inventory.q.${q.number}.hint", q.hint)
            )
        }

    private val defaultCategoryNamesSrc = listOf("Люди", "Учреждения", "Концепции")

    val defaultCategoryNames: List<String>
        get() = defaultCategoryNamesSrc.mapIndexed { i, label ->
            I18n.t("inventory.category.$i", label)
        }

    fun questionsOf(from: Int, to: Int): List<InventoryQuestion> =
        questions.filter { it.number in from..to }

    fun questionsGuideText(from: Int, to: Int): String =
        questionsOf(from, to).joinToString("\n") { "• ${it.title}" }

    val workThrough: String get() = I18n.t("inventory.workThrough", "Проработка обиды")
    val workThroughPro: String get() = I18n.t("inventory.workThroughPro", "Проработка обиды с ИИ")
    val workThroughHint: String get() = I18n.t(
        "inventory.workThroughHint",
        "ИИ положит черновики и слепые зоны в подсказки у полей (значок i). Если всё заполнено — даст полный разбор ситуации. Это не замена спонсора."
    )
    val workThroughNeedText: String get() = I18n.t(
        "inventory.workThroughNeedText",
        "Сначала напишите хотя бы причину, чувства, действия или один вопрос — тогда можно предложить остальные."
    )
    val workThroughAllFilled: String get() = I18n.t(
        "inventory.workThroughAllFilled",
        "Все вопросы этой ситуации уже заполнены."
    )
    val workThroughReadyHints: String get() = I18n.t(
        "inventory.workThroughReadyHints",
        "Готово: откройте подсказки у полей (значок i) — там черновики и слепые зоны."
    )
    val workThroughFullTitle: String get() = I18n.t(
        "inventory.workThroughFullTitle",
        "Полная проработка ситуации"
    )
    val insightDraftTitle: String get() = I18n.t("inventory.insightDraftTitle", "Черновик проработки")
    val insightBlindTitle: String get() = I18n.t(
        "inventory.insightBlindTitle",
        "Слепые зоны и слабые места"
    )
    val insertDraft: String get() = I18n.t("inventory.insertDraft", "Вставить в поле")
    val appendDraft: String get() = I18n.t("inventory.appendDraft", "Добавить в поле")
    val draftsTitle: String get() = I18n.t("inventory.draftsTitle", "Черновики для пустых вопросов")
    val insertAllDrafts: String get() = I18n.t("inventory.insertAllDrafts", "Вставить все")
    val dismissDraft: String get() = I18n.t("inventory.dismissDraft", "Скрыть")
    val dismissAnalysis: String get() = I18n.t("inventory.dismissAnalysis", "Скрыть разбор")
}
