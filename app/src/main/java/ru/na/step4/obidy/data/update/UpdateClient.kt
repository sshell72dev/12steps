package ru.na.step4.obidy.data.update

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import ru.na.step4.obidy.BuildConfig

/**
 * Публичный endpoint сервера: GET /api/v1/app-version.
 * Токен не требуется — версия доступна до всякой авторизации.
 */
object UpdateClient {
    private const val PATH = "/api/v1/app-version"

    fun fetch(): UpdateInfo? {
        val base = BuildConfig.ANALYSIS_API_URL.trimEnd('/')
        if (base.isBlank()) return null
        val connection = (URL("$base$PATH").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(JSONObject(raw))
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(root: JSONObject): UpdateInfo? {
        val versionCode = root.optInt("version_code", 0)
        val versionName = root.optString("version_name").trim()
        val apkUrl = root.optString("apk_url").trim()
        if (versionCode <= 0 || versionName.isBlank() || apkUrl.isBlank()) return null
        val rawNotes = root.optJSONArray("notes") ?: JSONArray()
        val notes = buildList {
            for (index in 0 until rawNotes.length()) {
                val item = rawNotes.optString(index).trim()
                if (item.isNotBlank()) add(item)
            }
        }
        return UpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            notes = notes,
            mandatory = root.optBoolean("mandatory", false),
            sizeBytes = root.optLong("size_bytes", 0L)
        )
    }
}
