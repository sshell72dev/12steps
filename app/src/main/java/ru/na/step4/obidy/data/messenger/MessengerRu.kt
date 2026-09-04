package ru.na.step4.obidy.data.messenger

import ru.na.step4.obidy.data.i18n.I18n

object MessengerRu {
    val title: String get() = I18n.t("messenger.title", "Мессенджер")
    val homeBody: String get() = I18n.t("messenger.homeBody", "Чаты с участниками по QR-коду: текст и голосовые, личные и группы.")
    val disabledTitle: String get() = I18n.t("messenger.disabledTitle", "Мессенджер временно отключён")
    val disabledBody: String get() = I18n.t("messenger.disabledBody", "Раздел выключен в админке. Дневник, самоанализ и психолог работают как раньше.")
    val nicknameTitle: String get() = I18n.t("messenger.nicknameTitle", "Как вас называть?")
    val nicknameBody: String get() = I18n.t("messenger.nicknameBody", "Это имя увидят те, с кем вы соединитесь по QR. Анкета «Моя личность» не меняется.")
    val nicknameHint: String get() = I18n.t("messenger.nicknameHint", "Никнейм")
    val continueLabel: String get() = I18n.t("messenger.continue", "Продолжить")
    val emptyChats: String get() = I18n.t("messenger.emptyChats", "Пока нет чатов. Покажите свой QR другу или отсканируйте его код.")
    val myQr: String get() = I18n.t("messenger.myQr", "Мой QR")
    val scanQr: String get() = I18n.t("messenger.scanQr", "Сканировать")
    val newGroup: String get() = I18n.t("messenger.newGroup", "Новая группа")
    val myQrTitle: String get() = I18n.t("messenger.myQrTitle", "Ваш код")
    val myQrHint: String get() = I18n.t("messenger.myQrHint", "Пусть друг наведёт камеру в приложении. Код можно обновить.")
    val rotateQr: String get() = I18n.t("messenger.rotateQr", "Новый код")
    val scanTitle: String get() = I18n.t("messenger.scanTitle", "Наведите на QR")
    val scanHint: String get() = I18n.t("messenger.scanHint", "Код друга или приглашение в группу.")
    val cameraPermission: String get() = I18n.t("messenger.cameraPermission", "Нужен доступ к камере, чтобы сканировать QR.")
    val grantCamera: String get() = I18n.t("messenger.grantCamera", "Разрешить камеру")
    val groupName: String get() = I18n.t("messenger.groupName", "Название группы")
    val groupNameHint: String get() = I18n.t("messenger.groupNameHint", "Например: утренняя группа")
    val addFriends: String get() = I18n.t("messenger.addFriends", "Добавить друзей")
    val noFriends: String get() = I18n.t("messenger.noFriends", "Пока нет друзей. Сначала соединитесь по QR.")
    val createGroup: String get() = I18n.t("messenger.createGroup", "Создать группу")
    val groupQr: String get() = I18n.t("messenger.groupQr", "QR группы")
    val members: String get() = I18n.t("messenger.members", "Участники")
    val addToGroup: String get() = I18n.t("messenger.addToGroup", "Добавить из друзей")
    val messageHint: String get() = I18n.t("messenger.messageHint", "Сообщение")
    val voiceMessage: String get() = I18n.t("messenger.voiceMessage", "Голосовое сообщение")
    val recording: String get() = I18n.t("messenger.recording", "Запись… отпустите, чтобы отправить")
    val cancelRecord: String get() = I18n.t("messenger.cancelRecord", "Отмена")
    val micPermission: String get() = I18n.t("messenger.micPermission", "Нужен микрофон для голосовых.")
    val today: String get() = I18n.t("messenger.today", "Сегодня")
    val yesterday: String get() = I18n.t("messenger.yesterday", "Вчера")
    val error: String get() = I18n.t("messenger.error", "Не получилось. Проверьте сеть и попробуйте ещё раз.")
    val selfInvite: String get() = I18n.t("messenger.selfInvite", "Это ваш код.")
    val badQr: String get() = I18n.t("messenger.badQr", "Этот QR не подходит для мессенджера.")
    val joined: String get() = I18n.t("messenger.joined", "Подключение выполнено.")
    val send: String get() = I18n.t("messenger.send", "Отправить")
    val owner: String get() = I18n.t("messenger.owner", "создатель")
    val alertsTitle: String get() = I18n.t("messenger.alertsTitle", "Оповещение")
    val alertsHow: String get() = I18n.t(
        "messenger.alertsHow",
        "Системные события приходят в этот чат и дублируются уведомлением на заставке и в шторке телефона."
    )
    val challenges: String get() = I18n.t("messenger.challenges", "Челленджи")
    val challengeSteps: String get() = I18n.t("messenger.challengeSteps", "Челлендж шагов")
    val challengeAnalysis: String get() = I18n.t("messenger.challengeAnalysis", "Челлендж самоанализов")
    val challengeStepsBody: String get() = I18n.t(
        "messenger.challengeStepsBody",
        "После записи в точку дневника в группу уходит ударный режим, название точки и рейтинг духовной деятельности."
    )
    val challengeAnalysisBody: String get() = I18n.t(
        "messenger.challengeAnalysisBody",
        "После прохождения самоанализа в группу уходит ударный режим, название самоанализа и рейтинг духовной деятельности."
    )
    val challengeJoin: String get() = I18n.t("messenger.challengeJoin", "Подключиться")
    val challengePoint: String get() = I18n.t("messenger.challengePoint", "Точка")
    val challengeAnalysisLabel: String get() = I18n.t("messenger.challengeAnalysisLabel", "Самоанализ")
    val challengeMembers: String get() = I18n.t("messenger.challengeMembers", "участников")

    fun challengeTitle(key: String, fallback: String): String = when (key) {
        "steps" -> challengeSteps
        "analysis" -> challengeAnalysis
        else -> fallback.ifBlank { challenges }
    }

    fun challengeBody(key: String): String = when (key) {
        "steps" -> challengeStepsBody
        "analysis" -> challengeAnalysisBody
        else -> ""
    }
}
