package ru.na.step4.obidy.data.analysis

import org.json.JSONArray
import org.json.JSONObject
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.ai.AiHttp
import ru.na.step4.obidy.data.profile.PersonalityPortrait
import ru.na.step4.obidy.data.profile.ProfileStore

object AnalysisAiClient {
    sealed class Result {
        data class Ok(
            val text: String,
            val personality: String? = null,
            val prompt: String = ""
        ) : Result()
        data class Err(val message: String) : Result()
    }

    fun analyze(
        title: String,
        answers: List<QaPair>,
        profile: ProfileStore? = null,
        premium: Boolean = false,
        admin: Boolean = false,
        goals: String? = null
    ): Result {
        val payload = JSONObject()
            .put("title", title)
            .put("premium", premium)
            .put("admin", admin)
            .put(
                "answers",
                JSONArray().also { arr ->
                    answers.forEach { pair ->
                        arr.put(
                            JSONObject()
                                .put("question", pair.question)
                                .put("answer", pair.answer)
                        )
                    }
                }
            )
        profile?.let {
            payload.put("questionnaire", questionnaireWithGoals(it, goals))
            payload.put(
                "personality",
                if (it.personalityCollectEnabled) it.personality else it.personalityForAi().orEmpty()
            )
            payload.put("name", it.name)
            payload.put("collect_personality", it.personalityCollectEnabled)
        }
        val language = ru.na.step4.obidy.data.i18n.I18n.languageCode()
            .ifBlank { profile?.languageCode.orEmpty() }
            .ifBlank { "ru" }
        payload.put("language", language)
        return when (val raw = AiHttp.post("/api/v1/analyze", payload, readTimeoutMs = 180_000)) {
            is AiHttp.Result.Err -> Result.Err(raw.message)
            is AiHttp.Result.Ok -> parse(raw.code, raw.body)
        }
    }

    private fun questionnaireWithGoals(profile: ProfileStore, goals: String?): String {
        val base = profile.questionnaireText().orEmpty()
        val extra = goals?.trim().orEmpty()
        return if (extra.isBlank()) base
        else if (base.isBlank()) extra
        else "$base\n\n$extra"
    }

    private fun parse(code: Int, raw: String): Result {
        val obj = AiHttp.parseObject(raw)
        if (code in 200..299) {
            val text = obj.optString("text").trim()
            val fromJson = obj.optString("personality").trim().ifBlank { null }
            val prompt = obj.optString("prompt").trim()
            val parsed = PersonalityPortrait.parse(text)
            val personality = fromJson ?: parsed.second
            val clean = parsed.first
            return if (clean.isBlank() && personality == null) {
                Result.Err(Ru.analysisAiError)
            } else {
                Result.Ok(clean.ifBlank { text }, personality, prompt)
            }
        }
        val message = when (obj.optString("error")) {
            "unauthorized", "not_configured" -> Ru.analysisAiNotConfigured
            "answers_required", "answers_empty" -> Ru.analysisAiError
            else -> Ru.analysisAiError
        }
        return Result.Err(message)
    }
}
