package ru.na.step4.obidy.data.i18n

import org.json.JSONArray
import org.json.JSONObject
import ru.na.step4.obidy.data.ai.AiHttp

object TranslateClient {
    data class Item(val key: String, val text: String)

    sealed class Result {
        data class Ok(val items: List<Item>) : Result()
        data class Err(val message: String) : Result()
    }

    fun translate(targetLanguage: String, items: List<Item>): Result {
        if (items.isEmpty()) return Result.Ok(emptyList())
        val payload = JSONObject()
            .put("target_language", targetLanguage)
            .put(
                "items",
                JSONArray().also { arr ->
                    items.forEach { item ->
                        arr.put(
                            JSONObject()
                                .put("key", item.key)
                                .put("text", item.text)
                        )
                    }
                }
            )
        return when (val raw = AiHttp.post("/api/v1/translate", payload, readTimeoutMs = 180_000)) {
            is AiHttp.Result.Err -> Result.Err(raw.message)
            is AiHttp.Result.Ok -> parse(raw.code, raw.body)
        }
    }

    private fun parse(code: Int, body: String): Result {
        val obj = AiHttp.parseObject(body)
        if (code !in 200..299) {
            return Result.Err(AiHttp.errorMessage(obj, "Translation failed"))
        }
        val arr = obj.optJSONArray("items") ?: return Result.Err("Translation failed")
        val out = ArrayList<Item>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val key = item.optString("key").trim()
            val text = item.optString("text").trim()
            if (key.isNotBlank() && text.isNotBlank()) out += Item(key, text)
        }
        return Result.Ok(out)
    }
}
