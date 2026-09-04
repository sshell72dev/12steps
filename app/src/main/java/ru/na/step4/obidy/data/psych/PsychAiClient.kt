package ru.na.step4.obidy.data.psych

import org.json.JSONArray
import org.json.JSONObject
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.ai.AiHttp
import ru.na.step4.obidy.data.profile.PersonalityPortrait

class PsychAiClient {
    sealed class Result {
        data class Ok(
            val text: String,
            val speakable: String,
            val question: String = "",
            val questions: List<String> = emptyList(),
            val personality: String? = null,
            val prompt: String = ""
        ) : Result()

        data class Err(val message: String) : Result()
    }

    fun request(
        kind: String,
        situation: String,
        answers: List<PsychQa>,
        settings: PsychSettings,
        noHistory: Boolean,
        topic: JSONObject?,
        topics: JSONArray? = null,
        questionNumber: Int = 1,
        questionCount: Int = 5,
        admin: Boolean = false
    ): Result {
        val payload = JSONObject()
            .put("kind", kind)
            .put("situation", situation)
            .put("language", settings.languageCode)
            .put("no_history", false)
            .put("question_number", questionNumber)
            .put("question_count", questionCount)
            .put("premium", settings.isPro)
            .put("admin", admin)
            .put("profile", PsychLogic.profileJson(settings))
            .put(
                "answers",
                JSONArray().also { arr ->
                    answers.forEach { qa ->
                        arr.put(
                            JSONObject()
                                .put("question", qa.question)
                                .put("answer", qa.answer)
                        )
                    }
                }
            )
        if (topics != null && topics.length() > 0) payload.put("topics", topics)
        if (topic != null) payload.put("topic", topic)
        return when (val raw = AiHttp.post("/api/v1/psych", payload, readTimeoutMs = 180_000)) {
            is AiHttp.Result.Err -> Result.Err(raw.message)
            is AiHttp.Result.Ok -> parse(raw.code, raw.body)
        }
    }

    private fun parse(code: Int, raw: String): Result {
        val obj = AiHttp.parseObject(raw)
        if (code in 200..299) {
            val text = obj.optString("text").trim()
            val question = obj.optString("question").trim()
            val speakableRaw = obj.optString("speakable").ifBlank { text }
            val fromJson = obj.optString("personality").trim().ifBlank { null }
            val parsedText = PersonalityPortrait.parse(text.ifBlank { speakableRaw })
            val parsedSpeak = PersonalityPortrait.parse(speakableRaw.ifBlank { text })
            val personality = fromJson ?: parsedText.second ?: parsedSpeak.second
            val cleanText = parsedText.first.ifBlank { parsedSpeak.first }.ifBlank { text }.ifBlank { speakableRaw }
            val speakable = parsedSpeak.first.ifBlank { cleanText }
            val questions = mutableListOf<String>()
            val arr = obj.optJSONArray("questions")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val q = arr.optString(i).trim()
                    if (q.isNotEmpty()) questions += q
                }
            }
            if (cleanText.isBlank() && question.isBlank() && questions.isEmpty()) {
                return Result.Err(Ru.analysisAiError)
            }
            return Result.Ok(
                text = cleanText.ifBlank { question },
                speakable = speakable.ifBlank { cleanText },
                question = question.ifBlank { cleanText },
                questions = questions,
                personality = personality,
                prompt = obj.optString("prompt").trim()
            )
        }
        return Result.Err(AiHttp.errorMessage(obj))
    }
}
