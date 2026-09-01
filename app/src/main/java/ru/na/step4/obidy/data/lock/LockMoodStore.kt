package ru.na.step4.obidy.data.lock

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.na.step4.obidy.data.ai.AiHttp
import java.time.LocalDate

class LockMoodStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cachedQuote(): String {
        if (prefs.getInt(KEY_VER, 0) != CACHE_VER) return fallback()
        val quote = prefs.getString(KEY_QUOTE, "").orEmpty()
        if (quote.isNotBlank() && prefs.getString(KEY_STAMP, "") == LocalDate.now().toString()) {
            return quote
        }
        return fallback()
    }

    suspend fun refresh(): String {
        val cached = cachedQuote()
        if (prefs.getInt(KEY_VER, 0) == CACHE_VER &&
            cached.isNotBlank() &&
            prefs.getString(KEY_STAMP, "") == LocalDate.now().toString() &&
            prefs.getBoolean(KEY_FROM_AI, false)
        ) {
            return cached
        }
        val ai = withContext(Dispatchers.IO) { fetchAi() }
        val quote = ai?.trim()?.takeIf { it.isNotBlank() } ?: fallback()
        prefs.edit()
            .putInt(KEY_VER, CACHE_VER)
            .putString(KEY_QUOTE, quote)
            .putString(KEY_STAMP, LocalDate.now().toString())
            .putBoolean(KEY_FROM_AI, ai != null)
            .apply()
        return quote
    }

    fun pickBackground(days: Long?, seed: Long): String {
        val pool = when {
            days == null || days < 7 -> listOf(
                "lock/lock_dawn.jpg", "lock/lock_mist.jpg", "lock/lock_path.jpg"
            )
            days < 30 -> listOf(
                "lock/lock_path.jpg", "lock/lock_dawn.jpg", "lock/lock_hills.jpg", "lock/lock_mist.jpg"
            )
            days < 90 -> listOf(
                "lock/lock_path.jpg", "lock/lock_hills.jpg", "lock/lock_light.jpg", "lock/lock_lake.jpg"
            )
            days < 365 -> listOf(
                "lock/lock_lake.jpg", "lock/lock_hills.jpg", "lock/lock_light.jpg", "lock/lock_path.jpg"
            )
            else -> listOf(
                "lock/lock_light.jpg", "lock/lock_lake.jpg", "lock/lock_hills.jpg"
            )
        }
        val idx = (seed ushr 1).toInt().mod(pool.size)
        return pool[idx]
    }

    private fun fetchAi(): String? {
        val payload = JSONObject()
            .put("role", "lock.quote")
            .put("user", USER_PROMPT)
            .put("language", ru.na.step4.obidy.data.i18n.I18n.languageCode())
            .put("max_tokens", 220)
        return when (val raw = AiHttp.post("/api/v1/chat", payload, readTimeoutMs = 20_000)) {
            is AiHttp.Result.Err -> null
            is AiHttp.Result.Ok -> {
                if (raw.code !in 200..299) return null
                AiHttp.parseObject(raw.body).optString("text").trim().ifBlank { null }
                    ?.lineSequence()
                    ?.filter { it.isNotBlank() }
                    ?.joinToString(" ")
                    ?.trim('"')
                    ?.take(280)
            }
        }
    }

    fun fallback(): String = FALLBACK.random()

    companion object {
        private const val PREFS = "lock_mood"
        private const val CACHE_VER = 5
        private const val KEY_VER = "ver"
        private const val KEY_QUOTE = "quote"
        private const val KEY_STAMP = "stamp"
        private const val KEY_FROM_AI = "from_ai"

        private const val USER_PROMPT =
            "Короткий философский текст на обложку. Без личности, имени и срока чистоты."

        private val FALLBACK = listOf(
            "Выздоровление начинается с честности — и продолжается одним днём.",
            "Чистота не требует всей жизни сразу. Достаточно сегодняшнего шага.",
            "Путь не в том, чтобы не упасть, а в том, чтобы не употреблять сегодня.",
            "Чистота растёт тихо: один честный день ложится на другой.",
            "Программа работает в той мере, в какой ей доверяют ещё на этот день.",
            "Смирение — не слабость, а ясность: не надо нести день в одиночку.",
            "Честность перед собой возвращает направление, когда ум предлагает обход.",
            "Долгая чистота — живая практика, а не титул. Её смысл — в следующем честном шаге."
        )
    }
}
