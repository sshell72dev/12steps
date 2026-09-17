package ru.na.step4.obidy.data.update

import ru.na.step4.obidy.data.i18n.I18n

object UpdateRu {
    val title: String get() = I18n.t("update.title", "Доступно обновление")
    val version: String get() = I18n.t("update.version", "Версия")
    val changes: String get() = I18n.t("update.changes", "Что нового")
    val updateNow: String get() = I18n.t("update.updateNow", "Обновить")
    val postpone: String get() = I18n.t("update.postpone", "Отложить")
    val downloading: String get() = I18n.t("update.downloading", "Скачиваю обновление")
    val downloadFailed: String get() = I18n.t(
        "update.downloadFailed",
        "Не удалось скачать обновление. Проверьте связь и попробуйте снова."
    )
    val permitNeeded: String get() = I18n.t(
        "update.permitNeeded",
        "Разрешите установку из этого источника и нажмите «Обновить» ещё раз."
    )
    val upToDate: String get() = I18n.t("update.upToDate", "У вас последняя версия.")
    val checkFailed: String get() = I18n.t(
        "update.checkFailed",
        "Не удалось проверить обновления. Попробуйте позже."
    )
    val check: String get() = I18n.t("update.check", "Проверить обновление")
    val checking: String get() = I18n.t("update.checking", "Проверяю…")
    val available: String get() = I18n.t("update.available", "Доступна версия")
    val openUpdate: String get() = I18n.t("update.openUpdate", "Обновить приложение")
    val postponedToChat: String get() = I18n.t(
        "update.postponedToChat",
        "Ссылка на обновление добавлена в чат — обновиться можно в любой момент."
    )
    val mandatory: String get() = I18n.t("update.mandatory", "Это обновление обязательно.")
    val kb: String get() = I18n.t("update.kb", "КБ")
    val mb: String get() = I18n.t("update.mb", "МБ")
}
