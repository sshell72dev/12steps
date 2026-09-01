package ru.na.step4.obidy.data.messenger

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import ru.na.step4.obidy.BuildConfig

internal object MessengerHttp {
    data class Response(val code: Int, val body: String)

    fun get(path: String, messengerId: String, readTimeoutMs: Int = 20_000): MessengerResult<Response> {
        return request("GET", path, messengerId, null, null, readTimeoutMs)
    }

    fun post(path: String, messengerId: String, payload: JSONObject, readTimeoutMs: Int = 20_000): MessengerResult<Response> {
        return request("POST", path, messengerId, payload, null, readTimeoutMs)
    }

    fun postMultipart(
        path: String,
        messengerId: String,
        file: File,
        durationMs: Int,
        readTimeoutMs: Int = 60_000
    ): MessengerResult<Response> {
        return request("POST", path, messengerId, null, Multipart(file, durationMs), readTimeoutMs)
    }

    fun getBytes(path: String, messengerId: String, readTimeoutMs: Int = 30_000): MessengerResult<ByteArray> {
        val base = BuildConfig.ANALYSIS_API_URL.trimEnd('/')
        val token = BuildConfig.ANALYSIS_API_TOKEN.trim()
        if (base.isBlank() || token.isBlank()) return MessengerResult.Err(MessengerRu.error)
        val url = if (path.startsWith("http")) path else "$base$path"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = readTimeoutMs
            doInput = true
            setRequestProperty("Accept", "*/*")
            setRequestProperty("X-Api-Token", token)
            if (messengerId.isNotBlank()) setRequestProperty("X-Messenger-Id", messengerId)
        }
        return try {
            val code = connection.responseCode
            if (code == 503) return MessengerResult.Disabled
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.readBytes() ?: ByteArray(0)
            if (code in 200..299) MessengerResult.Ok(bytes) else MessengerResult.Err(MessengerRu.error)
        } catch (_: IOException) {
            MessengerResult.Err(MessengerRu.error)
        } finally {
            connection.disconnect()
        }
    }

    private data class Multipart(val file: File, val durationMs: Int)

    private fun request(
        method: String,
        path: String,
        messengerId: String,
        payload: JSONObject?,
        multipart: Multipart?,
        readTimeoutMs: Int
    ): MessengerResult<Response> {
        val base = BuildConfig.ANALYSIS_API_URL.trimEnd('/')
        val token = BuildConfig.ANALYSIS_API_TOKEN.trim()
        if (base.isBlank() || token.isBlank()) return MessengerResult.Err(MessengerRu.error)
        val url = if (path.startsWith("http")) path else "$base$path"
        val boundary = "----Messenger${System.currentTimeMillis()}"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = readTimeoutMs
            doInput = true
            doOutput = payload != null || multipart != null
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Api-Token", token)
            if (messengerId.isNotBlank()) setRequestProperty("X-Messenger-Id", messengerId)
            when {
                multipart != null -> setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                payload != null -> setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (multipart != null) {
                connection.outputStream.use { out ->
                    val crlf = "\r\n"
                    fun write(text: String) = out.write(text.toByteArray(Charsets.UTF_8))
                    write("--$boundary$crlf")
                    write("Content-Disposition: form-data; name=\"duration_ms\"$crlf$crlf")
                    write("${multipart.durationMs}$crlf")
                    write("--$boundary$crlf")
                    write(
                        "Content-Disposition: form-data; name=\"file\"; filename=\"voice.m4a\"$crlf"
                    )
                    write("Content-Type: audio/mp4$crlf$crlf")
                    multipart.file.inputStream().use { it.copyTo(out) }
                    write(crlf)
                    write("--$boundary--$crlf")
                }
            } else if (payload != null) {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code == 503) return MessengerResult.Disabled
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            MessengerResult.Ok(Response(code, raw))
        } catch (_: IOException) {
            MessengerResult.Err(MessengerRu.error)
        } finally {
            connection.disconnect()
        }
    }

    fun parse(raw: String): JSONObject {
        return try {
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }
}
