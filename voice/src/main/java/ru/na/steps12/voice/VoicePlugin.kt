package ru.na.steps12.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class VoicePlugin(context: Context) {
    private val app = context.applicationContext
    val store = VoiceSettingsStore(app)
    val speaker = VoiceSpeaker(app, store)

    val config get() = store.config
    val snapshot get() = store.snapshot

    fun publicKey(fallback: String = ""): String =
        snapshot.publicKey.ifBlank { fallback }

    fun assistantId(fallback: String = ""): String =
        snapshot.assistantId.ifBlank { fallback }

    fun vapiVoice(): Map<String, Any> = snapshot.toVapiVoice()

    fun speak(text: String) = speaker.speak(text)

    fun stopSpeaking() = speaker.stop()

    suspend fun sync(baseUrl: String, token: String): Boolean = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || token.isBlank()) return@withContext false
        val url = "${baseUrl.trimEnd('/')}/api/v1/voice/config"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Api-Token", token)
        }
        try {
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) return@withContext false
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext false
            store.applyRemote(
                publicKey = obj.optString("public_key"),
                assistantId = obj.optString("assistant_id"),
                provider = obj.optString("voice_provider").ifBlank { VoiceCatalog.default.provider },
                voiceId = obj.optString("voice_id").ifBlank { VoiceCatalog.default.id },
                speed = obj.optDouble("speed", 1.0).toFloat()
            )
            true
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }
}
