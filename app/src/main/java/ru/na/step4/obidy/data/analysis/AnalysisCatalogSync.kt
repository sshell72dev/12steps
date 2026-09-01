package ru.na.step4.obidy.data.analysis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.na.step4.obidy.data.ai.AiHttp
import ru.na.step4.obidy.data.journal.JournalPrefs

object AnalysisCatalogSync {
    suspend fun sync(settings: AnalysisSettings, prefs: JournalPrefs) {
        if (prefs.isAdmin && settings.catalogDirty) {
            push(settings, prefs)
        }
        pull(settings, prefs)
    }

    suspend fun push(settings: AnalysisSettings, prefs: JournalPrefs): Boolean {
        val code = prefs.adminCode.trim()
        if (code.isBlank()) return false
        val entries = settings.remoteEntries()
        if (entries.isEmpty()) return false
        val catalog = AnalysisCatalog.encodeCatalog(entries)
        val payload = JSONObject()
            .put("code", code)
            .put("catalog", catalog)
        val result = withContext(Dispatchers.IO) {
            AiHttp.post("/api/v1/analyses", payload, readTimeoutMs = 30_000)
        }
        return when (result) {
            is AiHttp.Result.Err -> false
            is AiHttp.Result.Ok -> {
                if (result.code !in 200..299) return false
                val body = AiHttp.parseObject(result.body)
                if (!body.optBoolean("ok", result.code in 200..299)) return false
                val updated = body.optString("updated_at")
                settings.catalogDirty = false
                if (updated.isNotBlank()) {
                    prefs.analysesSyncedAt = updated
                    settings.replaceRemote(settings.remoteEntries(), updated)
                }
                true
            }
        }
    }

    suspend fun pull(settings: AnalysisSettings, prefs: JournalPrefs) {
        if (settings.catalogDirty) return
        val since = prefs.analysesSyncedAt
        val path = if (since.isBlank()) {
            "/api/v1/analyses"
        } else {
            "/api/v1/analyses?since=${java.net.URLEncoder.encode(since, "UTF-8")}"
        }
        val result = withContext(Dispatchers.IO) { AiHttp.get(path) }
        if (result !is AiHttp.Result.Ok || result.code !in 200..299) return
        val obj = AiHttp.parseObject(result.body)
        if (!obj.optBoolean("ok")) return
        if (obj.optBoolean("unchanged")) return
        val catalog = obj.optJSONObject("catalog") ?: return
        val updated = obj.optString("updated_at")
        val entries = AnalysisCatalog.parse(catalog.toString())
        if (entries.isEmpty() && catalog.optJSONArray("self_analyses") == null) return
        settings.replaceRemote(entries, updated)
        if (updated.isNotBlank()) prefs.analysesSyncedAt = updated
    }
}
