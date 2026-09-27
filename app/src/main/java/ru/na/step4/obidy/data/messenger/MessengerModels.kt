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
    val unread: Int = 0,
    val pinnedId: Long = 0L,
    val pinnedKind: String = "",
    val pinnedBody: String = "",
    val pinnedSender: String = ""
) {
    val isGroup: Boolean get() = kind == "group"
    val isAlerts: Boolean get() = kind == AppAlerts.KIND || id == AppAlerts.CHAT_ID

    /** Закреплённое сообщение показывается шапкой над лентой. */
    val hasPinned: Boolean get() = pinnedId > 0L

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
    val deleted: Boolean = false,
    val replyToId: Long = 0L,
    val replySenderName: String = "",
    val replyBody: String = "",
    val replyKind: String = "",
    val replyVoiceMs: Int = 0,
    val replyDeleted: Boolean = false,
    val forwardFrom: String = "",
    val reactions: List<MessengerReaction> = emptyList()
) {
    val isVoice: Boolean get() = kind == "voice"

    /** Реакции-эмодзи показываются чипами под текстом сообщения. */
    val hasReactions: Boolean get() = reactions.isNotEmpty()

    /** Системное сообщение о новой версии приложения — в чате рисуется активной кнопкой. */
    val isUpdate: Boolean get() = kind == "update"

    /** Сообщение изменено после отправки. */
    val isEdited: Boolean get() = editedAt > 0L && !deleted

    /** Сообщение отправлено ответом на другое. */
    val isReply: Boolean get() = replyToId > 0L

    /** Сообщение переслано из другого чата. */
    val isForwarded: Boolean get() = forwardFrom.isNotBlank()

    /** Текст цитаты: удалённый и голосовой оригинал показываются подписью. */
    fun replyPreview(): String = when {
        replyDeleted -> MessengerRu.messageDeleted
        replyKind == "voice" -> MessengerRu.voiceMessage
        else -> replyBody
    }
}

/** Реакция-эмодзи: count — сколько человек поставили, mine — ваша ли она. */
data class MessengerReaction(
    val emoji: String,
    val count: Int = 0,
    val mine: Boolean = false
)

/** Лента чата вместе с закреплённым сообщением: закреп приходит одним запросом с сообщениями. */
data class MessengerMessages(
    val items: List<MessengerMessage>,
    val pinned: MessengerMessage? = null
)

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
    val lastAt: Long = 0L,
    /** Ключ встроенной подгруппы челленджей: steps или analysis. */
    val key: String = "",
    /** Подгруппу можно переименовать или удалить — она создана самим участником. */
    val canManage: Boolean = false
)

/** Подгруппы группы и право завести свою. */
data class MessengerTopics(
    val items: List<MessengerTopic> = emptyList(),
    val canCreate: Boolean = false
)

data class MessengerJoinResult(
    val kind: String,
    val chatId: String,
    val title: String = "",
    val groupId: String = "",
    val challengeKey: String = "",
    val topics: List<MessengerChallengeTopic> = emptyList()
)

object MessengerChallengeKeys {
    const val STEPS = "steps"
    const val ANALYSIS = "analysis"
    const val SUPPORT = "support"
    const val HUB = "hub"
}

/** Подгруппа челленджей внутри группы: своя лента и свой ключ публикации. */
data class MessengerChallengeTopic(
    val key: String,
    val name: String,
    val chatId: String = ""
)

data class MessengerChallenge(
    val key: String,
    val name: String,
    val groupId: String = "",
    val chatId: String = "",
    val joined: Boolean = false,
    val members: Int = 0,
    val topics: List<MessengerChallengeTopic> = emptyList()
)

sealed class MessengerResult<out T> {
    data class Ok<T>(val value: T) : MessengerResult<T>()
    data object Disabled : MessengerResult<Nothing>()
    data class Err(val message: String = "") : MessengerResult<Nothing>()
}
