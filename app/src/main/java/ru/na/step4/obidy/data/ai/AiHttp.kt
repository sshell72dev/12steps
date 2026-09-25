package ru.na.step4.obidy.data.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import ru.na.step4.obidy.BuildConfig
import ru.na.step4.obidy.Ru

object AiHttp {
    sealed class Result {
        data class Ok(val code: Int, val body: String) : Result()
        data class Err(val message: String) : Result()
    }

    fun post(path: String, payload: JSONObject, readTimeoutMs: Int = 180_000): Result {
        return request("POST", path, payload, readTimeoutMs)
    }

    fun get(path: String, readTimeoutMs: Int = 20_000): Result {
        return request("GET", path, null, readTimeoutMs)
    }

    private fun request(
        method: String,
        path: String,
        payload: JSONObject?,
        readTimeoutMs: Int
    ): Result {
        val base = BuildConfig.ANALYSIS_API_URL.trimEnd('/')
        val token = BuildConfig.ANALYSIS_API_TOKEN.trim()
        if (base.isBlank() || token.isBlank()) {
            return Result.Err(Ru.analysisAiNotConfigured)
        }
        val url = if (path.startsWith("http")) path else "$base$path"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = readTimeoutMs
            doInput = true
            doOutput = payload != null
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Api-Token", token)
            if (payload != null) {
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (payload != null) {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            Result.Ok(code, raw)
        } catch (_: IOException) {
            Result.Err(Ru.analysisAiNetwork)
        } finally {
            connection.disconnect()
        }
    }

    fun parseObject(raw: String): JSONObject {
        return try {
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    /**
     * Текст ошибки для пользователя. Причину от сервера показывает только администратору:
     * остальным нужна понятная фраза, а не текст сбоя.
     * Причина берётся из поля "detail", а если ответ не JSON — из самого тела ответа.
     */
    fun errorMessage(
        obj: JSONObject,
        fallback: String = Ru.analysisAiError,
        raw: String = "",
        admin: Boolean = false
    ): String {
        return when (obj.optString("error")) {
            "unauthorized", "not_configured" -> Ru.analysisAiNotConfigured
            else -> {
                val cause = if (admin) obj.optString("detail").trim().ifBlank { plainText(raw) } else ""
                if (cause.isBlank()) fallback else "$fallback\n$cause"
            }
        }
    }

    /** Читаемый текст ответа, когда сервер вернул не JSON (например, HTML-страницу ошибки 500). */
    private fun plainText(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty() || text.startsWith("{")) return ""
        return text.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(300)
    }
}
