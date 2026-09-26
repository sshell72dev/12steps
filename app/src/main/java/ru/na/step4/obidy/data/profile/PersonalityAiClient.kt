package ru.na.step4.obidy.data.profile

import org.json.JSONObject
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.ai.AiHttp
import ru.na.step4.obidy.data.i18n.I18n

/**
 * Отдельный запрос на портрет «Моя личность».
 *
 * Проработка (ситуация у психолога, самоанализ, дневник, обиды) уходит своим запросом
 * и портрет больше не собирает. Портрет обновляет этот запрос — и только тогда,
 * когда пользователь включил «Мою личность» в анкете.
 */
object PersonalityAiClient {
    /** Откуда пришла проработка: сервер по этому коду подписывает материал в промпте. */
    object Source {
        const val PSYCH = "psych"
        const val ANALYSIS = "analysis"
        const val JOURNAL = "journal"
        const val INVENTORY = "inventory"
    }

    sealed class Result {
        data class Ok(val portrait: String, val prompt: String = "") : Result()
        data class Err(val message: String) : Result()
    }

    fun update(
        profile: ProfileStore,
        material: String,
        source: String,
        premium: Boolean = false,
        admin: Boolean = false
    ): Result {
        val text = material.trim()
        if (text.isEmpty()) return Result.Err(Ru.analysisAiError)
        val payload = JSONObject()
            .put("source", source)
            .put("material", text)
            .put("personality", profile.personality)
            .put("collect", true)
            .put("premium", premium)
            .put("admin", admin)
            .put("language", I18n.languageCode().ifBlank { profile.languageCode }.ifBlank { "ru" })
            .put("profile", profileJson(profile))
        profile.questionnaireText()?.takeIf { it.isNotBlank() }?.let { payload.put("questionnaire", it) }
        return when (val raw = AiHttp.post("/api/v1/personality", payload, readTimeoutMs = 120_000)) {
            is AiHttp.Result.Err -> Result.Err(raw.message)
            is AiHttp.Result.Ok -> parse(raw.code, raw.body, admin)
        }
    }

    /**
     * Обновляет портрет и сохраняет его в анкете.
     * Вызывать из IO-корутины; при выключенном портрете ничего не отправляет.
     */
    fun updateAndSave(
        profile: ProfileStore,
        material: String,
        source: String,
        premium: Boolean = false,
        admin: Boolean = false
    ): Boolean {
        if (!profile.personalityEnabled) return false
        val result = update(profile, material, source, premium, admin)
        val portrait = (result as? Result.Ok)?.portrait.orEmpty().trim()
        if (portrait.isEmpty()) return false
        profile.personality = portrait
        return true
    }

    private fun profileJson(profile: ProfileStore): JSONObject = JSONObject()
        .put("name", profile.name)
        .put("my_personality", profile.personality)
        .put("my_personality_collect_enabled", true)
        .put("my_personality_use_enabled", true)
        .put("recovery_program", profile.program)
        .put("language_code", profile.languageCode)

    private fun parse(code: Int, raw: String, admin: Boolean): Result {
        val obj = AiHttp.parseObject(raw)
        if (code in 200..299) {
            val portrait = obj.optString("personality").trim()
            if (portrait.isBlank()) return Result.Err(Ru.analysisAiError)
            return Result.Ok(portrait, obj.optString("prompt").trim())
        }
        return Result.Err(AiHttp.errorMessage(obj, Ru.analysisAiError, raw, admin))
    }
}
