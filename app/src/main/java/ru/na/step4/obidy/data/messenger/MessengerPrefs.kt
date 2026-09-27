package ru.na.step4.obidy.data.messenger

import android.content.Context
import java.util.UUID

class MessengerPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val messengerId: String
        get() {
            val stored = prefs.getString(KEY_ID, "").orEmpty()
            if (stored.isNotBlank()) return stored
            val created = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_ID, created).apply()
            return created
        }

    var displayName: String
        get() = prefs.getString(KEY_NAME, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_NAME, value.trim().take(40)).apply()
        }

    var enabledCached: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun challengeChatId(key: String): String =
        prefs.getString(challengeKey(key), "").orEmpty()

    fun putChallenges(items: List<MessengerChallenge>) {
        if (items.isEmpty()) return
        // Публикации челленджей адресует сервер, а локально помним только чат группы
        // «Челленджи»: через него открываются её подгруппы вместо ленты «Общий».
        val hub = items.firstOrNull { it.key == MessengerChallengeKeys.HUB }
        val editor = prefs.edit()
        if (hub != null && hub.joined && hub.chatId.isNotBlank()) {
            editor.putString(challengeKey(MessengerChallengeKeys.HUB), hub.chatId)
        } else if (hub != null) {
            editor.remove(challengeKey(MessengerChallengeKeys.HUB))
        }
        editor.apply()
    }

    fun putChallengeChat(key: String, chatId: String) {
        if (key.isBlank() || chatId.isBlank()) return
        prefs.edit().putString(challengeKey(key), chatId).apply()
    }

    private fun challengeKey(key: String) = "challenge_chat_$key"

    companion object {
        private const val PREFS = "messenger_prefs"
        private const val KEY_ID = "messenger_id"
        private const val KEY_NAME = "display_name"
        private const val KEY_ENABLED = "enabled_cached"
    }
}
