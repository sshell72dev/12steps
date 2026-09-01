package ru.na.step4.obidy.data.messenger

import android.net.Uri
import org.json.JSONObject

object MessengerInvite {
    const val SCHEME = "ru.na.steps12"
    const val HOST = "messenger"

    fun pairUri(token: String): String = "$SCHEME://$HOST/pair?token=${Uri.encode(token)}"

    fun groupUri(token: String): String = "$SCHEME://$HOST/group?token=${Uri.encode(token)}"

    fun parse(raw: String): String? {
        val text = raw.trim()
        if (text.isBlank()) return null
        parseUri(text)?.let { return it }
        runCatching {
            val obj = JSONObject(text)
            val token = obj.optString("token").trim()
            if (token.isNotBlank()) return token
        }
        if (text.matches(Regex("^[A-Za-z0-9_-]{8,80}$"))) return text
        return null
    }

    fun parseUri(raw: String): String? {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val ok = (uri.scheme == SCHEME && uri.host == HOST) ||
            (uri.scheme == "https" && uri.host?.contains("steps") == true && uri.path?.contains("messenger") == true)
        if (!ok && uri.scheme != SCHEME) return uri.getQueryParameter("token")?.trim()?.takeIf { it.isNotBlank() }
        if (uri.scheme == SCHEME && uri.host != HOST) return null
        return uri.getQueryParameter("token")?.trim()?.takeIf { it.isNotBlank() }
    }
}
