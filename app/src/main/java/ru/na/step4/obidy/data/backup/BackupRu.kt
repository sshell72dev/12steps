package ru.na.step4.obidy.data.backup

import ru.na.step4.obidy.data.i18n.I18n

object BackupRu {
    val title: String get() = I18n.t("backup.title", "Резервная копия")
    val hint: String get() = I18n.t(
        "backup.hint",
        "Копия всех данных сохраняется в один файл. Её можно перенести на новый телефон или загрузить после переустановки приложения. Файл не защищён паролем — храните его в надёжном месте."
    )
    val export: String get() = I18n.t("backup.export", "Сохранить копию в файл")
    val restore: String get() = I18n.t("backup.restore", "Восстановить из копии")
    val busy: String get() = I18n.t("backup.busy", "Идёт работа с копией, подождите…")
    val savedOk: String get() = I18n.t("backup.savedOk", "Копия сохранена")
    val savedError: String get() = I18n.t("backup.savedError", "Не удалось сохранить копию")
    val restoreTitle: String get() = I18n.t("backup.restoreTitle", "Восстановить данные?")
    val restoreBody: String get() = I18n.t(
        "backup.restoreBody",
        "Все данные на этом телефоне будут заменены данными из копии. Отменить это действие нельзя."
    )
    val restoreYes: String get() = I18n.t("backup.restoreYes", "Восстановить")
    val restoredOk: String get() = I18n.t("backup.restoredOk", "Данные восстановлены. Приложение перезапускается…")
    val restoredError: String get() = I18n.t(
        "backup.restoredError",
        "Не удалось восстановить: файл повреждён или это не копия «12 шагов»"
    )
}
