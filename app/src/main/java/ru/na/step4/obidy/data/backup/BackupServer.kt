package ru.na.step4.obidy.data.backup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.na.step4.obidy.BuildConfig
import ru.na.step4.obidy.Step4App
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Состояние копии на сервере: то, что показывает интерфейс. */
data class ServerSlot(
    val size: Long,
    val appVersion: String,
    val uploadedAt: String,
    val uploadCount: Int
)

/** Ответ сервера: ok = false означает отказ, code объясняет причину. */
data class ServerAnswer<T>(
    val ok: Boolean,
    val value: T? = null,
    val code: String = ""
)

/**
 * Аккаунт копий: вход по коду с почты и обмен архивами с сервером.
 *
 * Токен сессии и адрес почты лежат в отдельном файле настроек. Без входа
 * приложение работает как раньше: только локальные файлы копий.
 */
object BackupServer {

    const val MAX_BYTES = 50L * 1024 * 1024
    private const val PREFS = "backup_account"
    private const val KEY_EMAIL = "email"
    private const val KEY_TOKEN = "token"
    private const val CONNECT_TIMEOUT = 20_000
    private const val READ_TIMEOUT = 180_000

    fun email(context: Context): String =
        prefs(context).getString(KEY_EMAIL, "").orEmpty()

    fun token(context: Context): String =
        prefs(context).getString(KEY_TOKEN, "").orEmpty()

    fun isLinked(context: Context): Boolean = email(context).isNotBlank() && token(context).isNotBlank()

    fun logout(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun baseUrl(): String = BuildConfig.ANALYSIS_API_URL.trimEnd('/')

    suspend fun requestCode(context: Context, email: String): ServerAnswer<Unit> {
        val answer = sendJson(
            context = context,
            path = "/api/v1/backup/code",
            method = "POST",
            body = JSONObject().put("email", email).toString(),
            token = null
        )
        return ServerAnswer(answer.first, null, answer.second)
    }

    suspend fun login(context: Context, email: String, code: String, deviceId: String): ServerAnswer<Unit> {
        val body = JSONObject()
            .put("email", email)
            .put("code", code)
            .put("device_id", deviceId)
            .toString()
        val answer = sendJson(context, "/api/v1/backup/login", "POST", body, null)
        val json = answer.third ?: return ServerAnswer(false, null, answer.second)
        val token = json.optString("token")
        if (!answer.first || token.isBlank()) return ServerAnswer(false, null, answer.second)
        prefs(context).edit().putString(KEY_EMAIL, email).putString(KEY_TOKEN, token).apply()
        return ServerAnswer(true, null, "")
    }

    suspend fun meta(context: Context): ServerAnswer<ServerSlot?> {
        val answer = sendJson(context, "/api/v1/backup/meta", "GET", null, token(context))
        if (!answer.first) return ServerAnswer(false, null, answer.second)
        return ServerAnswer(true, parseSlot(answer.third), "")
    }

    /** Выгрузка архива. Пустой пароль: на сервере файл шифруется серверным ключом. */
    suspend fun upload(context: Context, archive: File): ServerAnswer<ServerSlot?> {
        if (archive.length() > MAX_BYTES) return ServerAnswer(false, null, "too_large")
        val answer = sendFile(context, archive)
        if (!answer.first) return ServerAnswer(false, null, answer.second)
        return ServerAnswer(true, parseSlot(answer.third), "")
    }

    /** Скачивание копии с сервера в указанный файл. */
    suspend fun download(context: Context, target: File): ServerAnswer<ServerSlot?> =
        withContext(Dispatchers.IO) {
            val url = URL("${baseUrl()}/api/v1/backup/latest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("X-Api-Token", BuildConfig.ANALYSIS_API_TOKEN)
                setRequestProperty("X-Backup-Token", token(context))
            }
            try {
                val status = connection.responseCode
                if (status != 200) {
                    ServerAnswer(false, null, errorCode(connection, status))
                } else {
                    target.parentFile?.mkdirs()
                    connection.inputStream.use { input ->
                        target.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                    ServerAnswer(true, null, "")
                }
            } catch (t: Throwable) {
                ServerAnswer(false, null, "network")
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun sendJson(
        context: Context,
        path: String,
        method: String,
        body: String?,
        token: String?
    ): Triple<Boolean, String, JSONObject?> = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl()}$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("X-Api-Token", BuildConfig.ANALYSIS_API_TOKEN)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!token.isNullOrBlank()) setRequestProperty("X-Backup-Token", token)
            if (body != null) doOutput = true
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val text = readBody(connection, status)
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (status == 200) {
                Triple(true, "", json)
            } else {
                Triple(false, json?.optString("error").orEmpty().ifBlank { "server" }, json)
            }
        } catch (t: Throwable) {
            Triple(false, "network", null)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun sendFile(
        context: Context,
        archive: File
    ): Triple<Boolean, String, JSONObject?> = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl()}/api/v1/backup")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            doOutput = true
            setFixedLengthStreamingMode(archive.length())
            setRequestProperty("X-Api-Token", BuildConfig.ANALYSIS_API_TOKEN)
            setRequestProperty("X-Backup-Token", token(context))
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("X-App-Version", BuildConfig.APP_VERSION_NAME)
            setRequestProperty("X-Device-Id", deviceIdOf(context))
        }
        try {
            connection.outputStream.use { output ->
                archive.inputStream().use { input -> input.copyTo(output) }
            }
            val status = connection.responseCode
            val text = readBody(connection, status)
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (status == 200) {
                Triple(true, "", json)
            } else {
                Triple(false, json?.optString("error").orEmpty().ifBlank { "server" }, json)
            }
        } catch (t: Throwable) {
            Triple(false, "network", null)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        return stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
    }

    private fun errorCode(connection: HttpURLConnection, status: Int): String {
        val text = connection.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        val code = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
        return code.ifBlank { if (status == 401) "auth" else "server" }
    }

    private fun parseSlot(json: JSONObject?): ServerSlot? {
        val slot = json?.optJSONObject("slot") ?: return null
        return ServerSlot(
            size = slot.optLong("size", 0L),
            appVersion = slot.optString("app_version"),
            uploadedAt = slot.optString("uploaded_at"),
            uploadCount = slot.optInt("upload_count", 0)
        )
    }

    /** Идентификатор устройства: тот же, что у остальных запросов приложения. */
    fun deviceIdOf(context: Context): String = runCatching {
        (context.applicationContext as Step4App).journalPrefs.deviceId
    }.getOrDefault("")
}
