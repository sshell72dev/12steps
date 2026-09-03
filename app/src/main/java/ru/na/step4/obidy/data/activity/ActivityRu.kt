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
    val analysisCount: String get() = I18n.t("activity.analysisCount", "Самоанализы")
    val questions: String get() = I18n.t("activity.questions", "Ответы на вопросы")
    val journalCount: String get() = I18n.t("activity.journalCount", "Записи дневника")
    val psychCount: String get() = I18n.t("activity.psychCount", "Сессии психолога")
    val aiCount: String get() = I18n.t("activity.aiCount", "Запросы к ИИ")
    val timeline: String get() = I18n.t("activity.timeline", "Лента действий")
    val conclusions: String get() = I18n.t("activity.conclusions", "Выводы")
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
