package ru.na.step4.obidy.data.backup

import ru.na.step4.obidy.data.i18n.I18n

object BackupRu {
    val title: String get() = I18n.t("backup.title", "Резервная копия")
    val hint: String get() = I18n.t(
        "backup.hint",
        "Копия всех данных: дневник, обиды, самоанализ, анкета, настройки. Файл можно унести на новый телефон, а на сервере копия обновляется раз в сутки."
    )
    val export: String get() = I18n.t("backup.export", "Сохранить копию в файл")
    val restore: String get() = I18n.t("backup.restore", "Восстановить из файла")
    val busy: String get() = I18n.t("backup.busy", "Идёт работа с копией, подождите…")
    val savedOk: String get() = I18n.t("backup.savedOk", "Копия сохранена")
    val savedError: String get() = I18n.t("backup.savedError", "Не удалось сохранить копию")
    val restoreTitle: String get() = I18n.t("backup.restoreTitle", "Восстановить данные?")
    val restoreBody: String get() = I18n.t(
        "backup.restoreBody",
        "Все данные на этом телефоне будут заменены данными из копии. Перед заменой я сохраню текущее состояние, чтобы можно было откатиться."
    )
    val restoreYes: String get() = I18n.t("backup.restoreYes", "Восстановить")
    val restoredOk: String get() = I18n.t("backup.restoredOk", "Данные восстановлены. Приложение перезапускается…")
    val restoredError: String get() = I18n.t(
        "backup.restoredError",
        "Не удалось восстановить: файл повреждён или это не копия «12 шагов»"
    )

    val passwordTitle: String get() = I18n.t("backup.passwordTitle", "Пароль на копию")
    val passwordHint: String get() = I18n.t(
        "backup.passwordHint",
        "Пароль шифрует файл. Запомните его: без пароля копию не открыть. Если оставить поле пустым, файл будет без защиты."
    )
    val passwordEnter: String get() = I18n.t("backup.passwordEnter", "Пароль копии")
    val passwordApply: String get() = I18n.t("backup.passwordApply", "Продолжить")
    val passwordWrong: String get() = I18n.t("backup.passwordWrong", "Неверный пароль или файл повреждён")
    val passwordEmpty: String get() = I18n.t("backup.passwordEmpty", "Пустой пароль — файл без защиты")

    val manifestTitle: String get() = I18n.t("backup.manifestTitle", "Копия найдена")
    val manifestOtherApp: String get() = I18n.t(
        "backup.manifestOtherApp",
        "Это копия другого приложения — восстановление отменено."
    )
    val manifestNewer: String get() = I18n.t(
        "backup.manifestNewer",
        "Копия сделана более новой версией приложения. Продолжить на свой риск?"
    )
    val sizeLabel: String get() = I18n.t("backup.sizeLabel", "Размер")

    val progressLabel: String get() = I18n.t("backup.progressLabel", "Записей")
    val rollbackSaved: String get() = I18n.t(
        "backup.rollbackSaved",
        "Текущее состояние сохранено — можно вернуться назад"
    )
    val rollbackAction: String get() = I18n.t("backup.rollbackAction", "Откатить восстановление")
    val rollbackDone: String get() = I18n.t("backup.rollbackDone", "Данные вернулись к состоянию до восстановления")
    val rollbackMissing: String get() = I18n.t("backup.rollbackMissing", "Автокопия не найдена")

    val serverTitle: String get() = I18n.t("backup.serverTitle", "Копия на сервере")
    val serverHint: String get() = I18n.t(
        "backup.serverHint",
        "Привяжите почту — приложение раз в сутки кладёт копию на сервер. Её можно вернуть на новом телефоне, даже если приложение удалили."
    )
    val serverEmail: String get() = I18n.t("backup.serverEmail", "Адрес почты")
    val serverSendCode: String get() = I18n.t("backup.serverSendCode", "Получить код")
    val serverCode: String get() = I18n.t("backup.serverCode", "Код из письма")
    val serverLogin: String get() = I18n.t("backup.serverLogin", "Войти")
    val serverCodeSent: String get() = I18n.t("backup.serverCodeSent", "Код отправлен на почту")
    val serverUpload: String get() = I18n.t("backup.serverUpload", "Сохранить на сервер")
    val serverDownload: String get() = I18n.t("backup.serverDownload", "Восстановить с сервера")
    val serverLogout: String get() = I18n.t("backup.serverLogout", "Отвязать почту")
    val serverLastPush: String get() = I18n.t("backup.serverLastPush", "Последняя выгрузка")
    val serverNoCopy: String get() = I18n.t("backup.serverNoCopy", "Копий на сервере пока нет")
    val serverUploaded: String get() = I18n.t("backup.serverUploaded", "Копия выгружена на сервер")
    val serverTooLarge: String get() = I18n.t("backup.serverTooLarge", "Копия больше допустимого размера")
    val serverNotConfigured: String get() = I18n.t(
        "backup.serverNotConfigured",
        "Сервер не принимает копии: обратитесь в поддержку"
    )
    val serverError: String get() = I18n.t("backup.serverError", "Не удалось связаться с сервером")
    val serverWrongCode: String get() = I18n.t("backup.serverWrongCode", "Код не подошёл")
    val serverBadEmail: String get() = I18n.t("backup.serverBadEmail", "Проверьте адрес почты")
}
