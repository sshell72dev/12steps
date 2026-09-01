package ru.na.step4.obidy.data.i18n

import android.content.Context
import org.json.JSONObject
import ru.na.step4.obidy.data.analysis.AnalysisCatalog
import ru.na.step4.obidy.data.journal.TreeCatalog
import ru.na.step4.obidy.data.journal.TreeNode
import ru.na.step4.obidy.data.profile.ProfileProblems
import ru.na.step4.obidy.data.profile.ProfileQuestionnaire

object ContentI18n {
    @Volatile
    private var registered = false

    fun registerStatic() {
        if (registered) return
        synchronized(this) {
            if (registered) return
            ProfileQuestionnaire.questions.forEach { q ->
                SourceCatalog.put("profile.q.${q.id}.text", q.text)
                if (q.hint.isNotBlank()) SourceCatalog.put("profile.q.${q.id}.hint", q.hint)
                q.options.forEachIndexed { index, option ->
                    SourceCatalog.put("profile.q.${q.id}.opt.$index", option)
                }
            }
            ProfileQuestionnaire.programs.forEachIndexed { index, program ->
                SourceCatalog.put("profile.program.$index", program)
            }
            ProfileProblems.all.forEach { opt ->
                SourceCatalog.put("profile.problem.${opt.key}", opt.label)
            }
            listOf(
                "inventory.POINT_A" to "Пункт А",
                "inventory.POINT_B" to "Пункт Б",
                "inventory.POINT_V" to "Пункт В",
                "inventory.POINT_G" to "Пункт Г",
                "inventory.INTRO_TITLE" to "I. Обиды",
                "inventory.INTRO" to "Наши обиды не давали нам покоя. Мы снова и снова проживали в своем сознании неприятные события из прошлого. Мы злились на то, что произошло, и вели подсчет всех своих обид. Мы сожалели о тех умных вещах, которых так и не сказали, и вынашивали планы расплаты за то, что было, или за то, чего могло и не быть. Мы были одержимы прошлым и будущим, и поэтому врали себе про настоящее. Теперь нам нужно написать об этих обидах, чтобы увидеть, какую роль мы сыграли в их появлении.",
                "inventory.POINT_A_BODY" to "Перечисли людей, организации и концепции, на которые ты обижен. Большинство из нас начинает с детства, но можно писать в любом порядке, главное, чтобы список был полным. Включи в него всех людей (родителей, друзей и подруг, врагов, самого себя и т. д.); организации и учреждения (тюрьмы, полицию, больницы, школы или институты и т. д.); а также концепции (религии, политику, предрассудки, социальные обычаи, Бога и т. д.), в отношении которых ты испытываешь чувство злости.",
                "inventory.POINT_B_BODY" to "Перечисли причину или причины каждой обиды. В каждой обиде мы изучаем причину нашей злости и то, как она повлияла на нас. Вот некоторые из вопросов, которые мы задаем себе, чтобы помочь себе определить наши чувства:",
                "inventory.POINT_V_BODY" to "В отношении каждой обиды мы стараемся увидеть, в чем мы совершили ошибку, и какую роль сыграли в ситуации. Какой была в каждой ситуации наша реакция на свои чувства? Мы должны быть максимально честны, мы должны разобраться, какие дефекты характера сыграли в наших действиях свою роль. Вот некоторые из вопросов, которые мы себе задаем:",
                "inventory.POINT_G_BODY" to "Ситуации, в которых мы уверены в своей правоте, требуют пристального внимания и обсуждения с нашим спонсором. Ответы на эти и другие вопросы применительно именно к нам самим помогают нам определить свои дефекты характера. Отвечать мы должны честно, не упуская ничего. Если где-то кто-то поступил с нами неправильно, мы должны осознать, что нам необходимо прекратить ожидания безупречности от других. Если мы рассчитываем когда-нибудь обрести мир в душе, мы должны научиться принимать других такими, какие они есть.",
                "inventory.TARGET_TITLE" to "На кого или на что я обижен?",
                "inventory.TARGET_HINT" to "Человек, организация, учреждение или концепция",
                "inventory.TYPE_SECTION" to "Типы ситуаций",
                "inventory.TYPE_SECTION_HINT" to "Одна обида может включать несколько типов ситуаций.",
                "inventory.SITUATION_SECTION" to "Ситуации",
                "inventory.SITUATION_SECTION_HINT" to "Для каждой обиды опишите причину, чувства, свою роль и ответьте на вопросы пунктов Б и В.",
                "inventory.TYPE_CUSTOM" to "Свой тип…",
                "inventory.WHAT_TITLE" to "Причина или причины обиды",
                "inventory.WHAT_HINT" to "Почему я злюсь и как это на меня повлияло",
                "inventory.FELT_TITLE" to "Я чувствовал",
                "inventory.FELT_HINT" to "Какие чувства были тогда и какие возвращаются. Можно выбрать из таблицы чувств.",
                "inventory.feelingsTable" to "Таблица чувств",
                "inventory.DID_TITLE" to "Я делал",
                "inventory.DID_HINT" to "Как я реагировал: что говорил, делал или не делал",
                "inventory.Q_SECTION" to "Вопросы к обиде",
                "inventory.Q_SECTION_HINT" to "Пункт Б — вопросы 1–4. Пункт В — вопросы 5–12. Можно заполнять по частям.",
                "inventory.workThrough" to "Проработка обиды",
                "inventory.workThroughPro" to "Проработка обиды с ИИ",
                "inventory.workThroughHint" to "По уже написанному ИИ предложит черновики ответов на пустые вопросы этой ситуации. Это не замена спонсора — проверьте и поправьте своим языком.",
                "inventory.workThroughNeedText" to "Сначала напишите хотя бы причину, чувства, действия или один вопрос — тогда можно предложить остальные.",
                "inventory.workThroughAllFilled" to "Все вопросы этой ситуации уже заполнены.",
                "inventory.draftsTitle" to "Черновики для пустых вопросов",
                "inventory.insertDraft" to "Вставить в поле",
                "inventory.insertAllDrafts" to "Вставить все",
                "inventory.dismissDraft" to "Скрыть"
            ).forEach { (key, value) -> SourceCatalog.put(key, value) }
            listOf(
                "Критика / унижение",
                "Предательство",
                "Игнор",
                "Контроль",
                "Невыполненные обещания",
                "Насилие / угроза",
                "Разочарование в ожиданиях"
            ).forEachIndexed { i, label ->
                SourceCatalog.put("inventory.type.$i", label)
            }
            listOf("Люди", "Учреждения", "Концепции").forEachIndexed { i, label ->
                SourceCatalog.put("inventory.category.$i", label)
            }
            listOf(
                1 to ("Была ли причиной моей обиды гордость?" to "Гордость как причина злости."),
                2 to ("Угрожало ли что-либо моей безопасности или моему благополучию?" to "Безопасность и благополучие."),
                3 to ("Были ли задеты или поставлены под угрозу личные отношения или сексуальные связи?" to "Личные отношения или сексуальные связи."),
                4 to ("Привели ли мои желания к конфликту с другими?" to "Желания и конфликт с другими."),
                5 to ("Где в основе моих действий были жадность или потребность подчинить?" to "Жадность или потребность подчинить."),
                6 to ("До каких крайностей я доходил в своих обидах?" to "Крайности в обидах."),
                7 to ("Каким образом я манипулировал другими, и зачем?" to "Манипуляции и цель."),
                8 to ("В чем был эгоизм моего поведения?" to "Эгоизм поведения."),
                9 to ("Думал ли я, что жизнь мне что-то должна?" to "Ожидание, что жизнь мне должна."),
                10 to ("Каким образом мои ожидания от других приводили к проблемам?" to "Ожидания от других и проблемы."),
                11 to ("Как в этой ситуации проявлялись гордыня и эго?" to "Гордыня и эго."),
                12 to ("Как меня мотивировал страх?" to "Страх как мотив."),
                13 to ("Какие чувства я не умел или не был готов проживать, и как я их избегал?" to "Непрожитые чувства и способы избегания.")
            ).forEach { (n, pair) ->
                SourceCatalog.put("inventory.q.$n.title", pair.first)
                SourceCatalog.put("inventory.q.$n.hint", pair.second)
            }
            registered = true
        }
    }

    fun registerTree(context: Context) {
        val catalog = TreeCatalog.load(context)
        catalog.byId.values.forEach { node ->
            registerNode(node)
        }
    }

    fun registerNode(node: TreeNode) {
        SourceCatalog.put("tree.${node.id}.name", node.name)
        if (node.description.isNotBlank()) {
            SourceCatalog.put("tree.${node.id}.description", node.description)
        }
        if (node.botLabel.isNotBlank()) {
            SourceCatalog.put("tree.${node.id}.bot_label", node.botLabel)
        }
    }

    fun localizedName(node: TreeNode): String =
        I18n.t("tree.${node.id}.name", node.name)

    fun localizedDescription(node: TreeNode): String =
        if (node.description.isBlank()) ""
        else I18n.t("tree.${node.id}.description", node.description)

    fun localizedBotLabel(node: TreeNode): String =
        if (node.botLabel.isBlank()) ""
        else I18n.t("tree.${node.id}.bot_label", node.botLabel)

    fun registerAnalysisCatalog(context: Context) {
        val entries = AnalysisCatalog.load(context)
        entries.forEach { entry ->
            SourceCatalog.put("analysis.${entry.id}.title", entry.title)
            entry.questions.forEachIndexed { index, q ->
                SourceCatalog.put("analysis.${entry.id}.q.$index", q.text)
                q.prayer?.let { prayer ->
                    if (prayer.title.isNotBlank()) {
                        SourceCatalog.put("analysis.${entry.id}.q.$index.prayerTitle", prayer.title)
                    }
                    if (prayer.text.isNotBlank()) {
                        SourceCatalog.put("analysis.${entry.id}.q.$index.prayerText", prayer.text)
                    }
                }
                q.choices.forEach { choice ->
                    SourceCatalog.put("analysis.${entry.id}.choice.${choice.id}", choice.label)
                }
            }
            entry.branches.forEach { branch ->
                SourceCatalog.put("analysis.${entry.id}.branch.${branch.id}", branch.title)
            }
            entry.items.forEachIndexed { index, item ->
                SourceCatalog.put("analysis.${entry.id}.item.$index.title", item.title)
                SourceCatalog.put("analysis.${entry.id}.item.$index.question", item.question)
                SourceCatalog.put("analysis.${entry.id}.item.$index.yes", item.ifYes.label)
                SourceCatalog.put("analysis.${entry.id}.item.$index.no", item.ifNo.label)
            }
        }
        SourceCatalog.put("analysis.cleanDay.title", "Чистый день")
    }

    fun localizedAnalysisTitle(id: String, source: String): String =
        I18n.t("analysis.$id.title", source)

    fun localizedAnalysisQuestion(id: String, index: Int, source: String): String =
        I18n.t("analysis.$id.q.$index", source)

    fun noteKey(id: String, field: String): String = "note.$id.$field"

    fun localizedNote(id: String, field: String, source: String): String {
        if (source.isBlank()) return source
        val key = noteKey(id, field)
        SourceCatalog.put(key, source)
        return I18n.t(key, source)
    }
}
