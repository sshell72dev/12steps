package ru.na.step4.obidy.data.book

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.na.step4.obidy.data.ai.AiHttp
import ru.na.step4.obidy.data.i18n.I18n

/** Одна правка текста: что было в главе и что предлагает редактор. */
data class BookFix(
    val old: String,
    val new: String,
    val reason: String
)

/**
 * Исправления выбранных правок: правки идут по порядку текста, поэтому каждую
 * следующую ищем после предыдущей замены — так одинаковые фрагменты не путаются,
 * а удаление фрагмента (пустое new) работает как есть.
 */
fun applyBookFixes(text: String, fixes: List<BookFix>): String {
    var result = text
    var cursor = 0
    fixes.forEach { fix ->
        if (fix.old.isBlank() || fix.old == fix.new) return@forEach
        val at = result.indexOf(fix.old, cursor).takeIf { it >= 0 }
            ?: result.indexOf(fix.old)
        if (at < 0) return@forEach
        result = result.substring(0, at) + fix.new + result.substring(at + fix.old.length)
        cursor = at + fix.new.length
    }
    return result
}

/** Проверка главы книги: текст уходит на сервер, обратно приходит список правок. */
object BookAiClient {
    sealed class Result {
        data class Ok(val fixes: List<BookFix>) : Result()
        data class Err(val message: String) : Result()
    }

    suspend fun fixText(text: String, premium: Boolean = false, admin: Boolean = false): Result =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext Result.Ok(emptyList())
            val payload = JSONObject()
                .put("text", text)
                .put("premium", premium)
                .put("language", I18n.languageCode().ifBlank { "ru" })
            when (val raw = AiHttp.post("/api/v1/book/fix", payload, readTimeoutMs = 180_000)) {
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
            // Пустое new — это удаление фрагмента: редактор убирает оговорку или повтор.
            if (old.isBlank() || old == new) continue
            fixes.add(BookFix(old = old, new = new, reason = item.optString("reason").trim()))
        }
        return Result.Ok(fixes)
    }
}
