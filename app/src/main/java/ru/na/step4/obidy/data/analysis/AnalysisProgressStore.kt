package ru.na.step4.obidy.data.analysis

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class AnalysisProgressStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val lock = Any()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun lastActiveId(): String? = synchronized(lock) {
        val id = read().optString(KEY_LAST_ACTIVE).ifBlank { return@synchronized null }
        if (session(id) == null) null else id
    }

    fun session(catalogId: String): JSONObject? = synchronized(lock) {
        read().optJSONObject(KEY_SESSIONS)?.optJSONObject(catalogId)
    }

    fun pausedCatalogIds(): Set<String> = synchronized(lock) {
        val obj = read().optJSONObject(KEY_SESSIONS) ?: return emptySet()
        obj.keys().asSequence().toSet()
    }

    fun save(catalogId: String, session: JSONObject, markActive: Boolean) {
        synchronized(lock) {
            val root = read()
            val sessions = root.optJSONObject(KEY_SESSIONS) ?: JSONObject()
            sessions.put(catalogId, session)
            root.put(KEY_SESSIONS, sessions)
            if (markActive) {
                root.put(KEY_LAST_ACTIVE, catalogId)
            } else if (root.optString(KEY_LAST_ACTIVE) == catalogId) {
                root.remove(KEY_LAST_ACTIVE)
            }
            write(root)
        }
    }

    fun clear(catalogId: String) {
        synchronized(lock) {
            val root = read()
            val sessions = root.optJSONObject(KEY_SESSIONS) ?: JSONObject()
            sessions.remove(catalogId)
            root.put(KEY_SESSIONS, sessions)
            if (root.optString(KEY_LAST_ACTIVE) == catalogId) {
                root.remove(KEY_LAST_ACTIVE)
            }
            write(root)
        }
    }

    fun clearLastActive() {
        synchronized(lock) {
            val root = read()
            if (!root.has(KEY_LAST_ACTIVE)) return
            root.remove(KEY_LAST_ACTIVE)
            write(root)
        }
    }

    private fun read(): JSONObject {
        if (!file.exists()) return JSONObject()
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .getOrDefault(JSONObject())
    }

    private fun write(root: JSONObject) {
        file.writeText(root.toString(), Charsets.UTF_8)
        _revision.value = _revision.value + 1
    }

    companion object {
        private const val FILE_NAME = "analysis-progress.json"
        private const val KEY_LAST_ACTIVE = "last_active"
        private const val KEY_SESSIONS = "sessions"
    }
}
