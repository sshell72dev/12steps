package ru.na.step4.obidy.data.notes

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.na.step4.obidy.data.ai.AiHttp
import ru.na.step4.obidy.data.journal.JournalPrefs

class NotesRepository(
    context: Context,
    private val prefs: JournalPrefs
) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val mutex = Mutex()
    private val _notes = MutableStateFlow(readFile())
    val notes: StateFlow<Map<String, NoteOverride>> = _notes.asStateFlow()

    private val _isAdmin = MutableStateFlow(prefs.isAdmin)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    fun resolved(
        id: String,
        defaultText: String,
        defaultTitle: String = "",
        defaultMode: NoteMode = NoteMode.POPUP
    ): ResolvedNote {
        val override = _notes.value[id]
        val textRaw = override?.text?.takeIf { it.isNotBlank() } ?: defaultText
        val mode = override?.mode ?: defaultMode
        val titleRaw = override?.title?.takeIf { it.isNotBlank() } ?: defaultTitle
        val showTitle = override?.showTitle ?: false
        val text = ru.na.step4.obidy.data.i18n.ContentI18n.localizedNote(id, "text", textRaw)
        val title = ru.na.step4.obidy.data.i18n.ContentI18n.localizedNote(id, "title", titleRaw)
        return ResolvedNote(id = id, title = title, text = text, mode = mode, showTitle = showTitle)
    }

    suspend fun sync() {
        val since = prefs.notesSyncedAt
        val path = if (since.isBlank()) "/api/v1/notes" else "/api/v1/notes?since=${java.net.URLEncoder.encode(since, "UTF-8")}"
        when (val result = withContext(Dispatchers.IO) { AiHttp.get(path) }) {
            is AiHttp.Result.Err -> Unit
            is AiHttp.Result.Ok -> {
                if (result.code !in 200..299) return
                val obj = AiHttp.parseObject(result.body)
                val arr = obj.optJSONArray("notes") ?: JSONArray()
                if (arr.length() == 0 && since.isNotBlank()) return
                mutex.withLock {
                    val next = _notes.value.toMutableMap()
                    var latest = since
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val note = parseNote(item, dirty = false) ?: continue
                        val existing = next[note.id]
                        if (existing?.dirty == true) continue
                        next[note.id] = note
                        if (note.updatedAt > latest) latest = note.updatedAt
                    }
                    persistLocked(next)
                    if (latest.isNotBlank()) prefs.notesSyncedAt = latest
                }
            }
        }
        pushDirty()
    }

    suspend fun activateAdmin(code: String): Boolean {
        val trimmed = code.trim().uppercase()
        if (trimmed.isBlank()) return false
        val payload = JSONObject().put("code", trimmed)
        val result = withContext(Dispatchers.IO) {
            AiHttp.post("/api/v1/admin/activate", payload, readTimeoutMs = 20_000)
        }
        val ok = when (result) {
            is AiHttp.Result.Err -> false
            is AiHttp.Result.Ok -> result.code in 200..299 && AiHttp.parseObject(result.body).optBoolean("ok")
        }
        if (ok) {
            prefs.adminCode = trimmed
            prefs.isAdmin = true
            _isAdmin.value = true
        }
        return ok
    }

    fun clearAdmin() {
        prefs.adminCode = ""
        prefs.isAdmin = false
        _isAdmin.value = false
    }

    suspend fun save(id: String, title: String, text: String, mode: NoteMode, showTitle: Boolean): Boolean {
        val local = NoteOverride(
            id = id,
            title = title.trim(),
            text = text,
            mode = mode,
            showTitle = showTitle,
            updatedAt = "",
            dirty = true
        )
        mutex.withLock {
            persistLocked(_notes.value + (id to local))
        }
        val code = prefs.adminCode
        if (code.isBlank()) return false
        val payload = JSONObject()
            .put("code", code)
            .put("id", id)
            .put("title", local.title)
            .put("text", local.text)
            .put("mode", mode.api)
            .put("show_title", local.showTitle)
        val result = withContext(Dispatchers.IO) {
            AiHttp.post("/api/v1/notes", payload, readTimeoutMs = 20_000)
        }
        return when (result) {
            is AiHttp.Result.Err -> false
            is AiHttp.Result.Ok -> {
                if (result.code !in 200..299) return false
                val saved = AiHttp.parseObject(result.body).optJSONObject("note")
                val remote = saved?.let { parseNote(it, dirty = false) }
                mutex.withLock {
                    persistLocked(_notes.value + (id to (remote ?: local.copy(dirty = false))))
                }
                true
            }
        }
    }

    private suspend fun pushDirty() {
        val code = prefs.adminCode
        if (code.isBlank()) return
        val dirty = _notes.value.values.filter { it.dirty }
        dirty.forEach { note ->
            save(note.id, note.title, note.text, note.mode, note.showTitle)
        }
    }

    private suspend fun persistLocked(map: Map<String, NoteOverride>) {
        _notes.value = map
        withContext(Dispatchers.IO) {
            val arr = JSONArray()
            map.values.forEach { note ->
                arr.put(
                    JSONObject()
                        .put("id", note.id)
                        .put("title", note.title)
                        .put("text", note.text)
                        .put("mode", note.mode.api)
                        .put("showTitle", note.showTitle)
                        .put("updatedAt", note.updatedAt)
                        .put("dirty", note.dirty)
                )
            }
            file.writeText(JSONObject().put("notes", arr).toString(), Charsets.UTF_8)
        }
    }

    private fun readFile(): Map<String, NoteOverride> {
        if (!file.exists()) return emptyMap()
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("notes") ?: JSONArray()
            val out = LinkedHashMap<String, NoteOverride>()
            for (i in 0 until arr.length()) {
                parseNote(arr.getJSONObject(i), arr.getJSONObject(i).optBoolean("dirty"))?.let {
                    out[it.id] = it
                }
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseNote(obj: JSONObject, dirty: Boolean): NoteOverride? {
        val id = obj.optString("id")
        if (id.isBlank()) return null
        return NoteOverride(
            id = id,
            title = obj.optString("title"),
            text = obj.optString("text").ifBlank { obj.optString("body") },
            mode = NoteMode.fromApi(obj.optString("mode")),
            showTitle = obj.optBoolean("show_title") || obj.optBoolean("showTitle"),
            updatedAt = obj.optString("updated_at").ifBlank { obj.optString("updatedAt") },
            dirty = dirty
        )
    }

    companion object {
        private const val FILE_NAME = "notes-cache.json"
    }
}
