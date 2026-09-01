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

    fun errorMessage(obj: JSONObject, fallback: String = Ru.analysisAiError): String {
        return when (obj.optString("error")) {
            "unauthorized", "not_configured" -> Ru.analysisAiNotConfigured
            else -> fallback
        }
    }
}
