package ru.na.step4.obidy.data.messenger

import ru.na.step4.obidy.data.alerts.AppAlerts

data class MessengerUser(
    val id: String = "",
    val displayName: String = "",
    val avatarUrl: String = ""
)

data class MessengerChat(
    val id: String,
    val kind: String,
    val title: String,
    val peerId: String = "",
    val groupId: String = "",
    val isOwner: Boolean = false,
    val avatarUrl: String = "",
    val lastBody: String = "",
    val lastKind: String = "",
    val lastAt: Long = 0L,
    val unread: Int = 0
) {
    val isGroup: Boolean get() = kind == "group"
    val isAlerts: Boolean get() = kind == AppAlerts.KIND || id == AppAlerts.CHAT_ID

    /** Служебный чат: пишет само приложение, аватар — иконка вместо буквы. */
    val isService: Boolean get() = isAlerts || kind == "service"
}

data class MessengerMessage(
    val id: Long,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val kind: String,
    val body: String,
    val voiceDurationMs: Int,
    val createdAt: Long,
    val mine: Boolean,
    val editedAt: Long = 0L,
    val deleted: Boolean = false
) {
    val isVoice: Boolean get() = kind == "voice"

    /** Системное сообщение о новой версии приложения — в чате рисуется активной кнопкой. */
    val isUpdate: Boolean get() = kind == "update"

    /** Сообщение изменено после отправки. */
    val isEdited: Boolean get() = editedAt > 0L && !deleted
}

data class MessengerContact(
    val id: String,
    val displayName: String,
    val avatarUrl: String = ""
)

data class MessengerGroupInfo(
    val id: String,
    val name: String,
    val ownerId: String,
    val isOwner: Boolean,
    val canManage: Boolean = false,
    val avatarUrl: String = "",
    val members: List<MessengerContact>,
    val token: String,
    val chatId: String
)

/** Подгруппа внутри группы — как тема в мессенджере: своя лента сообщений. */
data class MessengerTopic(
    val id: String,
    val name: String,
    val chatId: String = "",
    val isGeneral: Boolean = false,
    val unread: Int = 0,
    val lastBody: String = "",
    val lastKind: String = "",
    val lastAt: Long = 0L
)

data class MessengerJoinResult(
    val kind: String,
    val chatId: String,
    val title: String = "",
    val groupId: String = "",
    val challengeKey: String = ""
)

object MessengerChallengeKeys {
    const val STEPS = "steps"
    const val ANALYSIS = "analysis"
    const val SUPPORT = "support"
}

data class MessengerChallenge(
    val key: String,
    val name: String,
    val groupId: String = "",
    val chatId: String = "",
    val joined: Boolean = false,
    val members: Int = 0
)

sealed class MessengerResult<out T> {
    data class Ok<T>(val value: T) : MessengerResult<T>()
    data object Disabled : MessengerResult<Nothing>()
    data class Err(val message: String = "") : MessengerResult<Nothing>()
}
