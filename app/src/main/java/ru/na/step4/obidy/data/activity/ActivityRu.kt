package ru.na.step4.obidy.data.activity

import ru.na.step4.obidy.data.i18n.I18n

object ActivityRu {
    val title: String get() = I18n.t("activity.title", "Моя статистика")
    val eyebrow: String get() = I18n.t("activity.eyebrow", "Трекер")
    val homeBody: String get() = I18n.t(
        "activity.homeBody",
        "Все действия в приложении: самоанализы, дневник, психолог, ИИ и прослушивание — с временем от и до."
    )
    val day: String get() = I18n.t("activity.day", "День")
    val week: String get() = I18n.t("activity.week", "Неделя")
    val month: String get() = I18n.t("activity.month", "Месяц")
    val totalTime: String get() = I18n.t("activity.totalTime", "Всего в приложении")
    val listenTime: String get() = I18n.t("activity.listenTime", "Прослушивание")
    val analysisTime: String get() = I18n.t("activity.analysisTime", "Время самоанализа")
    val psychTime: String get() = I18n.t("activity.psychTime", "Время с психологом")
    val inventoryTime: String get() = I18n.t("activity.inventoryTime", "Время инвентаря")
    val journalTime: String get() = I18n.t("activity.journalTime", "Время дневника")
    val aiTime: String get() = I18n.t("activity.aiTime", "Время ИИ-запросов")
    val analysisCount: String get() = I18n.t("activity.analysisCount", "Самоанализы")
    val questions: String get() = I18n.t("activity.questions", "Ответы на вопросы")
    val journalCount: String get() = I18n.t("activity.journalCount", "Записи дневника")
    val psychCount: String get() = I18n.t("activity.psychCount", "Сессии психолога")
    val inventoryCount: String get() = I18n.t("activity.inventoryCount", "Ситуации инвентаря")
    val aiCount: String get() = I18n.t("activity.aiCount", "Запросы к ИИ")
    val sessions: String get() = I18n.t("activity.sessions", "Сессии")
    val showAll: String get() = I18n.t("activity.showAll", "Все события")
    val showMain: String get() = I18n.t("activity.showMain", "Основные")
    val timeline: String get() = I18n.t("activity.timeline", "Лента действий")
    val conclusions: String get() = I18n.t("activity.conclusions", "Выводы")
    val unfinished: String get() = I18n.t("activity.unfinished", "Не завершён")
    val fromTo: String get() = I18n.t("activity.fromTo", "%1\$s — %2\$s")
    val insightTotal: String get() = I18n.t("activity.insightTotal", "В приложении вы провели %1\$s.")
    val insightAnalysis: String get() = I18n.t(
        "activity.insightAnalysis",
        "Завершено самоанализов: %1\$d. Ответов: %2\$d. Время разбора: %3\$s."
    )
    val insightUnfinished: String get() = I18n.t(
        "activity.insightUnfinished",
        "Самоанализ не доведён до конца (%1\$d отв., %2\$s). Стоит закончить разбор."
    )
    val insightJournal: String get() = I18n.t("activity.insightJournal", "Записей в дневнике: %1\$d.")
    val insightPsych: String get() = I18n.t("activity.insightPsych", "Сессий с электронным психологом: %1\$d.")
    val insightInventory: String get() = I18n.t("activity.insightInventory", "Работы над ситуациями инвентаря: %1\$d.")
    val insightAi: String get() = I18n.t("activity.insightAi", "Обращений к ИИ: %1\$d.")
    val insightListen: String get() = I18n.t(
        "activity.insightListen",
        "Прослушиваний вслух: %1\$d, всего %2\$s."
    )
    val insightShort: String get() = I18n.t(
        "activity.insightShort",
        "Короткий заход: практики почти не было. Даже 10 минут самоанализа уже меняют день."
    )
    val insightCombo: String get() = I18n.t(
        "activity.insightCombo",
        "Самоанализ и дневник шли рядом — так материал закрепляется лучше."
    )
    val insightListenLong: String get() = I18n.t(
        "activity.insightListenLong",
        "Вы слушали ответы дольше пяти минут: полезно возвращаться к своим словам, а не только писать."
    )
    val insightSeries: String get() = I18n.t(
        "activity.insightSeries",
        "Плотная серия самоанализов. Имеет смысл отметить, что именно повторяется в ответах."
    )
    val insightInventoryAi: String get() = I18n.t(
        "activity.insightInventoryAi",
        "Инвентарь шёл вместе с ИИ-разбором — ответы и подсказки лучше сверить ещё раз на следующий день."
    )
    val empty: String get() = I18n.t(
        "activity.empty",
        "За выбранный период действий пока нет. Они появятся, как только вы начнёте работу в приложении."
    )
    val results: String get() = I18n.t("activity.results", "Результаты")

    fun duration(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> I18n.t("activity.durHms", "%1\$d ч %2\$d мин").format(h, m)
            m > 0 -> I18n.t("activity.durMs", "%1\$d мин %2\$d сек").format(m, s)
            else -> I18n.t("activity.durSec", "%1\$d сек").format(s)
        }
    }

    fun category(cat: String): String = when (cat) {
        ActivityCat.ANALYSIS -> I18n.t("activity.catAnalysis", "Самоанализ")
        ActivityCat.PSYCH -> I18n.t("activity.catPsych", "Психолог")
        ActivityCat.JOURNAL -> I18n.t("activity.catJournal", "Дневник")
        ActivityCat.INVENTORY -> I18n.t("activity.catInventory", "Инвентарь")
        ActivityCat.AI -> I18n.t("activity.catAi", "ИИ")
        ActivityCat.LISTEN -> I18n.t("activity.catListen", "Прослушивание")
        ActivityCat.SCREEN -> I18n.t("activity.catScreen", "Экран")
        else -> cat
    }

    fun type(type: String): String = when (type) {
        ActivityType.START -> I18n.t("activity.typeStart", "Начало")
        ActivityType.ANSWER -> I18n.t("activity.typeAnswer", "Ответ")
        ActivityType.FINISH -> I18n.t("activity.typeFinish", "Завершение")
        ActivityType.AI -> I18n.t("activity.typeAi", "ИИ-анализ")
        ActivityType.LISTEN_START -> I18n.t("activity.typeListenStart", "Начало прослушивания")
        ActivityType.LISTEN_END -> I18n.t("activity.typeListenEnd", "Конец прослушивания")
        ActivityType.SCREEN -> I18n.t("activity.typeScreen", "Экран")
        ActivityType.SAVE -> I18n.t("activity.typeSave", "Сохранение")
        else -> type
    }
}
