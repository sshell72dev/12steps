package ru.na.step4.obidy.data.messenger

import ru.na.step4.obidy.data.alerts.AppAlerts

data class MessengerUser(
    val id: String = "",
    val displayName: String = ""
)

data class MessengerChat(
    val id: String,
    val kind: String,
    val title: String,
    val peerId: String = "",
    val groupId: String = "",
    val isOwner: Boolean = false,
    val lastBody: String = "",
    val lastKind: String = "",
    val lastAt: Long = 0L,
    val unread: Int = 0
) {
    val isGroup: Boolean get() = kind == "group"
    val isAlerts: Boolean get() = kind == AppAlerts.KIND || id == AppAlerts.CHAT_ID
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
    val mine: Boolean
) {
    val isVoice: Boolean get() = kind == "voice"
}

data class MessengerContact(
    val id: String,
    val displayName: String
)

data class MessengerGroupInfo(
    val id: String,
    val name: String,
    val ownerId: String,
    val isOwner: Boolean,
    val members: List<MessengerContact>,
    val token: String,
    val chatId: String
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
