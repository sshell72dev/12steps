package ru.na.step4.obidy.data.spiritual

import ru.na.step4.obidy.data.i18n.I18n

object SpiritualRu {
    val abbr: String get() = I18n.t("spiritual.abbr", "ДД")
    val title: String get() = I18n.t("spiritual.title", "Духовная Деятельность")
    val badgeCd: String get() = I18n.t("spiritual.badgeCd", "Статистика духовной деятельности")
    val day: String get() = I18n.t("spiritual.day", "День")
    val total: String get() = I18n.t("spiritual.total", "Общий ДД")
    val rate: String get() = I18n.t("spiritual.rate", "Курс")
    val practiceStreak: String get() = I18n.t("spiritual.practiceStreak", "Серия практики")
    val missWindow: String get() = I18n.t("spiritual.missWindow", "Окно пропуска")
    val events: String get() = I18n.t("spiritual.events", "Начисления")
    val emptyEvents: String get() = I18n.t("spiritual.emptyEvents", "Пока нет начислений.")
    val dayDip: String get() = I18n.t("spiritual.dayDip", "просадка")
    val daySilence: String get() = I18n.t("spiritual.daySilence", "тишина")
    val daySoft: String get() = I18n.t("spiritual.daySoft", "мягкий день")
    val dayWorking: String get() = I18n.t("spiritual.dayWorking", "в работе")
    val dayDeep: String get() = I18n.t("spiritual.dayDeep", "глубокий день")
    val srcAnalysis: String get() = I18n.t("spiritual.srcAnalysis", "самоанализ")
    val srcJournal: String get() = I18n.t("spiritual.srcJournal", "дневник")
    val srcPsych: String get() = I18n.t("spiritual.srcPsych", "психолог")
    val srcSupport: String get() = I18n.t("spiritual.srcSupport", "сообщение об ошибке")
    val srcAi: String get() = I18n.t("spiritual.srcAi", "оценка ИИ")
    val srcMiss: String get() = I18n.t("spiritual.srcMiss", "пропуск")
    val reasonAnalysis: String get() = I18n.t("spiritual.reasonAnalysis", "Завершён самоанализ")
    val reasonJournal: String get() = I18n.t("spiritual.reasonJournal", "Запись в дневнике")
    val reasonPsych: String get() = I18n.t("spiritual.reasonPsych", "Проработка у психолога")
    val reasonSupport: String get() = I18n.t("spiritual.reasonSupport", "Сообщение об ошибке")
    val reasonMiss: String get() = I18n.t("spiritual.reasonMiss", "День без практики")
    val reasonFictitious: String get() = I18n.t("spiritual.reasonFictitious", "Фиктивная или поверхностная проработка")
    val aiDefaultReason: String get() = I18n.t("spiritual.aiDefaultReason", "Оценка ИИ")
}
