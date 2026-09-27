package ru.na.step4.obidy.data.book

import org.json.JSONObject
import ru.na.step4.obidy.data.ai.AiHttp
import ru.na.step4.obidy.data.i18n.I18n

/** Одна правка текста: что было в главе и что предлагает редактор. */
data class BookFix(
    val old: String,
    val new: String,
    val reason: String
)

/** Исправления выбранных правок: заменяем каждый фрагмент на его исправление. */
fun applyBookFixes(text: String, fixes: List<BookFix>): String {
    var result = text
    fixes.forEach { fix ->
        if (fix.old.isNotBlank() && fix.old != fix.new) {
            result = result.replaceFirst(fix.old, fix.new)
        }
    }
    return result
}

/** Проверка главы книги: текст уходит на сервер, обратно приходит список правок. */
object BookAiClient {
    sealed class Result {
        data class Ok(val fixes: List<BookFix>) : Result()
        data class Err(val message: String) : Result()
    }

    fun fixText(text: String, premium: Boolean = false, admin: Boolean = false): Result {
        if (text.isBlank()) return Result.Ok(emptyList())
        val payload = JSONObject()
            .put("text", text)
            .put("premium", premium)
            .put("language", I18n.languageCode().ifBlank { "ru" })
        return when (val raw = AiHttp.post("/api/v1/book/fix", payload, readTimeoutMs = 180_000)) {
            is AiHttp.Result.Err -> Result.Err(raw.message)
            is AiHttp.Result.Ok -> parse(raw.code, raw.body, admin)
        }
    }

    private fun parse(code: Int, raw: String, admin: Boolean): Result {
        val obj = AiHttp.parseObject(raw)
        if (code !in 200..299) {
            return Result.Err(AiHttp.errorMessage(obj, BookRu.fixError, raw, admin))
        }
        val arr = obj.optJSONArray("edits") ?: return Result.Ok(emptyList())
        val fixes = ArrayList<BookFix>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val old = item.optString("old")
            val new = item.optString("new")
            if (old.isBlank() || new.isBlank() || old == new) continue
            fixes.add(BookFix(old = old, new = new, reason = item.optString("reason").trim()))
        }
        return Result.Ok(fixes)
    }
}
