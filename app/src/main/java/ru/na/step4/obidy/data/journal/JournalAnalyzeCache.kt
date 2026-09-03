package ru.na.step4.obidy.data.journal

import android.content.Context
import java.io.File
import org.json.JSONObject

data class CachedEntryAnalyze(
    val text: String,
    val entryUpdatedAt: Long,
    val textHash: Int
) {
    fun matches(entry: JournalEntry): Boolean =
        entry.updatedAt == entryUpdatedAt && entry.text.hashCode() == textHash
}

/** Cached AI analysis per journal entry (invalidated when entry text changes). */
class JournalAnalyzeCache(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val lock = Any()

    fun get(entryId: String): CachedEntryAnalyze? {
        if (entryId.isBlank()) return null
        return synchronized(lock) {
            val entry = read().optJSONObject(KEY_ENTRIES)?.optJSONObject(entryId) ?: return null
            val text = entry.optString("text").trim()
            if (text.isBlank()) return null
            CachedEntryAnalyze(
                text = text,
                entryUpdatedAt = entry.optLong("entryUpdatedAt"),
                textHash = entry.optInt("textHash")
            )
        }
    }

    fun save(entry: JournalEntry, analysis: String) {
        if (entry.id.isBlank() || analysis.isBlank()) return
        synchronized(lock) {
            val root = read()
            val all = root.optJSONObject(KEY_ENTRIES) ?: JSONObject()
            all.put(
                entry.id,
                JSONObject()
                    .put("text", analysis)
                    .put("entryUpdatedAt", entry.updatedAt)
                    .put("textHash", entry.text.hashCode())
                    .put("savedAt", System.currentTimeMillis())
            )
            root.put(KEY_ENTRIES, all)
            write(root)
        }
    }

    fun clear(entryId: String) {
        if (entryId.isBlank()) return
        synchronized(lock) {
            val root = read()
            val all = root.optJSONObject(KEY_ENTRIES) ?: return
            if (!all.has(entryId)) return
            all.remove(entryId)
            root.put(KEY_ENTRIES, all)
            write(root)
        }
    }

    private fun read(): JSONObject {
        if (!file.exists()) return JSONObject()
        return try {
            JSONObject(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun write(root: JSONObject) {
        file.writeText(root.toString(), Charsets.UTF_8)
    }

    companion object {
        private const val FILE_NAME = "journal-analyze-cache.json"
        private const val KEY_ENTRIES = "entries"
    }
}
