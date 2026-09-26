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
    val updateNow: String get() = I18n.t("messenger.updateNow", "Обновить приложение")
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
        "После записи в точку дневника в группу уходит ударный режим, название и рейтинг духовной деятельности, а после работы по IP — отметка о проработке обиды и рейтинг."
    )
    val challengeAnalysisBody: String get() = I18n.t(
        "messenger.challengeAnalysisBody",
        "После прохождения самоанализа в группу уходит ударный режим, название самоанализа и рейтинг духовной деятельности."
    )
    val challengeJoin: String get() = I18n.t("messenger.challengeJoin", "Подключиться")
    val challengePoint: String get() = I18n.t("messenger.challengePoint", "Точка")
    val challengeAnalysisLabel: String get() = I18n.t("messenger.challengeAnalysisLabel", "Самоанализ")
    val challengeInventoryDone: String get() = I18n.t("messenger.challengeInventoryDone", "Проработка обиды")
    val challengeMembers: String get() = I18n.t("messenger.challengeMembers", "участников")
    val groupInfo: String get() = I18n.t("messenger.groupInfo", "О группе")
    val topicsTitle: String get() = I18n.t("messenger.topicsTitle", "Темы")
    val topicsEmpty: String get() = I18n.t("messenger.topicsEmpty", "Пока нет тем — создайте первую.")
    val topicsScreenHint: String get() = I18n.t(
        "messenger.topicsScreenHint",
        "Темы — подгруппы внутри группы: у каждой своя лента. «Общий» — лента всей группы."
    )
    val topicGeneral: String get() = I18n.t("messenger.topicGeneral", "Общий")
    val topicCreate: String get() = I18n.t("messenger.topicCreate", "Новая тема")
    val topicRename: String get() = I18n.t("messenger.topicRename", "Переименовать тему")
    val topicDelete: String get() = I18n.t("messenger.topicDelete", "Удалить тему")
    val topicDeleteQuestion: String get() = I18n.t(
        "messenger.topicDeleteQuestion",
        "Удалить эту тему вместе с её лентой сообщений?"
    )
    val topicNameHint: String get() = I18n.t("messenger.topicNameHint", "Название темы")
    val topicNotFound: String get() = I18n.t("messenger.topicNotFound", "Тема не найдена.")
    val confirmYes: String get() = I18n.t("messenger.confirmYes", "Удалить")
    val confirmNo: String get() = I18n.t("messenger.confirmNo", "Отмена")
    val save: String get() = I18n.t("messenger.save", "Сохранить")
    val photoTooLarge: String get() = I18n.t("messenger.photoTooLarge", "Фото слишком большое — выберите меньше.")
    val photoBadFormat: String get() = I18n.t(
        "messenger.photoBadFormat",
        "Не получилось прочитать изображение. Возьмите другое фото."
    )
    val groupLocked: String get() = I18n.t(
        "messenger.groupLocked",
        "Группа челленджа закрыта: выходить и переименовывать её нельзя."
    )
    val ownerImmutable: String get() = I18n.t(
        "messenger.ownerImmutable",
        "Создателя группы нельзя удалить или заменить."
    )
    val nameRequired: String get() = I18n.t("messenger.nameRequired", "Укажите название.")
    val noRights: String get() = I18n.t("messenger.noRights", "Недостаточно прав для этого действия.")
    val profileTitle: String get() = I18n.t("messenger.profileTitle", "Профиль")
    val photoHint: String get() = I18n.t(
        "messenger.photoHint",
        "Фото видят те, с кем вы переписываетесь."
    )
    val photoAdd: String get() = I18n.t("messenger.photoAdd", "Добавить фото")
    val photoChange: String get() = I18n.t("messenger.photoChange", "Сменить фото")
    val photoDelete: String get() = I18n.t("messenger.photoDelete", "Удалить фото")
    val groupPhotoAdd: String get() = I18n.t("messenger.groupPhotoAdd", "Добавить фото группы")
    val groupSettings: String get() = I18n.t("messenger.groupSettings", "Настройки группы")
    val renameHint: String get() = I18n.t("messenger.renameHint", "Название группы")
    val topicsOpen: String get() = I18n.t("messenger.topicsOpen", "Темы")
    val topicsHint: String get() = I18n.t(
        "messenger.topicsHint",
        "Темы — подгруппы внутри группы: у каждой своя лента сообщений."
    )
    val deleteGroup: String get() = I18n.t("messenger.deleteGroup", "Удалить группу")
    val deleteGroupQuestion: String get() = I18n.t(
        "messenger.deleteGroupQuestion",
        "Удалить группу? Она исчезнет у всех участников."
    )
    val removeMember: String get() = I18n.t("messenger.removeMember", "Удалить")
    val removeMemberQuestion: String get() = I18n.t(
        "messenger.removeMemberQuestion",
        "Удалить участника из группы?"
    )
    val messageActions: String get() = I18n.t("messenger.messageActions", "Сообщение")
    val messageEdit: String get() = I18n.t("messenger.messageEdit", "Редактировать")
    val messageEditTitle: String get() = I18n.t("messenger.messageEditTitle", "Правка сообщения")
    val messageDelete: String get() = I18n.t("messenger.messageDelete", "Удалить сообщение")
    val messageDeleteQuestion: String get() = I18n.t(
        "messenger.messageDeleteQuestion",
        "Удалить сообщение? Оно исчезнет у всех участников."
    )
    val messageEdited: String get() = I18n.t("messenger.messageEdited", "изменено")
    val messageDeleted: String get() = I18n.t("messenger.messageDeleted", "Сообщение удалено")

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
