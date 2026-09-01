package ru.na.step4.obidy.data.analysis

import android.content.Context
import java.security.MessageDigest
import org.json.JSONArray

class AnalysisAiCache(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(title: String, answers: List<QaPair>): String? {
        val text = prefs.getString(entryKey(title, answers), null)?.trim().orEmpty()
        return text.ifBlank { null }
    }

    fun put(title: String, answers: List<QaPair>, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val key = hash(title, answers)
        val keys = keys().toMutableList().apply {
            remove(key)
            add(0, key)
        }
        while (keys.size > LIMIT) {
            val dropped = keys.removeAt(keys.lastIndex)
            prefs.edit().remove(PREFIX + dropped).apply()
        }
        prefs.edit()
            .putString(PREFIX + key, clean)
            .putString(KEY_INDEX, JSONArray(keys).toString())
            .apply()
    }

    private fun keys(): List<String> {
        val raw = prefs.getString(KEY_INDEX, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }

    private fun entryKey(title: String, answers: List<QaPair>): String =
        PREFIX + hash(title, answers)

    private fun hash(title: String, answers: List<QaPair>): String {
        val language = ru.na.step4.obidy.data.i18n.I18n.languageCode()
        val raw = buildString {
            append(language)
            append('\n')
            append(title.trim())
            answers.forEach { pair ->
                append('\n')
                append(pair.question.trim())
                append('\n')
                append(pair.answer.trim())
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    companion object {
        private const val PREFS = "analysis_ai_cache"
        private const val PREFIX = "rev_"
        private const val KEY_INDEX = "index"
        private const val LIMIT = 40
    }
}
