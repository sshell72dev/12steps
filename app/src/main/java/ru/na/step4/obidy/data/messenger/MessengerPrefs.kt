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
        val editor = prefs.edit()
        val known = items.map { it.key }.toMutableSet()
        known += listOf(MessengerChallengeKeys.STEPS, MessengerChallengeKeys.ANALYSIS)
        known.forEach { key ->
            val item = items.find { it.key == key }
            if (item != null && item.joined && item.chatId.isNotBlank()) {
                editor.putString(challengeKey(key), item.chatId)
            } else {
                editor.remove(challengeKey(key))
            }
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
