package ru.na.step4.obidy.data.analysis

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object AnalysisAnswers {
    fun encode(pairs: List<QaPair>): String {
        val arr = JSONArray()
        pairs.forEach { pair ->
            arr.put(
                JSONObject()
                    .put("q", pair.question)
                    .put("a", pair.answer)
            )
        }
        return arr.toString()
    }

    fun decode(json: String): List<QaPair> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            QaPair(o.optString("q"), o.optString("a"))
        }
    }

    fun asShareText(title: String, createdAt: Long, pairs: List<QaPair>): String {
        val date = formatDate(createdAt)
        return buildString {
            appendLine(title)
            appendLine(date)
            appendLine()
            pairs.forEach { pair ->
                appendLine(pair.question)
                appendLine("\u2014 ${pair.answer}")
                appendLine()
            }
        }.trimEnd()
    }

    /** Text for device TTS: questions and answers in order. */
    fun asSpeakText(
        pairs: List<QaPair>,
        questionLabel: String,
        answerLabel: String
    ): String = buildString {
        pairs.forEachIndexed { index, pair ->
            if (index > 0) append("\n\n")
            val q = pair.question.trim()
            val a = pair.answer.trim()
            if (q.isNotEmpty()) {
                append(questionLabel)
                append(". ")
                append(q)
                if (!q.endsWith('?') && !q.endsWith('.') && !q.endsWith('!')) append('.')
            }
            if (a.isNotEmpty()) {
                if (isNotEmpty()) append('\n')
                append(answerLabel)
                append(". ")
                append(a)
                if (!a.endsWith('.') && !a.endsWith('!') && !a.endsWith('?')) append('.')
            }
        }
    }.trim()

    fun formatDate(millis: Long): String {
        val locale = ru.na.step4.obidy.data.i18n.I18n.locale()
        return SimpleDateFormat("d MMMM yyyy, HH:mm", locale).format(Date(millis))
    }
}

class AnalysisSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val remoteFile = File(appContext.filesDir, REMOTE_FILE)

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    var cleanDayLong: Boolean
        get() = prefs.getBoolean(KEY_CLEAN_LONG, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CLEAN_LONG, value).apply()
            bump()
        }

    fun cleanDayId(): String =
        if (cleanDayLong) "clean-day-long" else "clean-day-short"

    fun overrides(): Map<String, CatalogEntry> {
        val raw = prefs.getString(KEY_OVERRIDES, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val out = linkedMapOf<String, CatalogEntry>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                runCatching {
                    out[key] = AnalysisCatalog.parseEntry(obj.getJSONObject(key))
                }
            }
            out
        }.getOrDefault(emptyMap())
    }

    fun customEntries(): List<CatalogEntry> {
        val raw = prefs.getString(KEY_CUSTOM, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                AnalysisCatalog.parseEntry(arr.getJSONObject(i)).copy(custom = true)
            }
        }.getOrDefault(emptyList())
    }

    fun isOverridden(id: String): Boolean = overrides().containsKey(id)

    fun saveOverride(entry: CatalogEntry) {
        if (entry.custom) {
            saveCustom(entry)
            return
        }
        val obj = currentOverridesObject()
        obj.put(entry.id, AnalysisCatalog.encodeEntry(entry.copy(custom = false)))
        prefs.edit().putString(KEY_OVERRIDES, obj.toString()).apply()
        bump()
    }

    fun clearOverride(id: String) {
        val obj = currentOverridesObject()
        obj.remove(id)
        prefs.edit().putString(KEY_OVERRIDES, obj.toString()).apply()
        bump()
    }

    fun addCustom(title: String): CatalogEntry {
        val existing = customEntries()
        val entry = AnalysisCatalog.blankCustom(title, 200 + existing.size)
        writeCustom(existing + entry)
        return entry
    }

    fun saveCustom(entry: CatalogEntry) {
        val list = customEntries().toMutableList()
        val index = list.indexOfFirst { it.id == entry.id }
        val saved = entry.copy(custom = true)
        if (index >= 0) list[index] = saved else list += saved
        writeCustom(list)
    }

    fun deleteCustom(id: String) {
        writeCustom(customEntries().filterNot { it.id == id })
    }

    fun remoteEntries(): List<CatalogEntry> {
        if (!remoteFile.exists()) return emptyList()
        return runCatching {
            AnalysisCatalog.parse(remoteFile.readText(Charsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    fun replaceRemote(entries: List<CatalogEntry>, updatedAt: String = "") {
        remoteFile.writeText(AnalysisCatalog.encodeCatalog(entries).toString(), Charsets.UTF_8)
        if (updatedAt.isNotBlank()) {
            prefs.edit().putString(KEY_REMOTE_AT, updatedAt).apply()
        }
        bump()
    }

    fun remoteUpdatedAt(): String = prefs.getString(KEY_REMOTE_AT, "").orEmpty()

    var catalogDirty: Boolean
        get() = prefs.getBoolean(KEY_DIRTY, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DIRTY, value).apply()
        }

    fun applyStandardEntry(entry: CatalogEntry, defaults: List<CatalogEntry>) {
        val current = remoteEntries().ifEmpty { defaults }.toMutableList()
        val index = current.indexOfFirst { it.id == entry.id }
        if (index >= 0) current[index] = entry else current += entry
        if (entry.custom) deleteCustom(entry.id) else clearOverride(entry.id)
        catalogDirty = true
        replaceRemote(current)
    }

    fun removeStandardEntry(id: String, defaults: List<CatalogEntry>) {
        val current = remoteEntries().ifEmpty { defaults }.filterNot { it.id == id }
        deleteCustom(id)
        clearOverride(id)
        catalogDirty = true
        replaceRemote(current)
    }

    fun addStandardCustom(title: String, defaults: List<CatalogEntry>): CatalogEntry {
        val current = remoteEntries().ifEmpty { defaults }
        val entry = AnalysisCatalog.blankCustom(title, 200 + current.size)
        applyStandardEntry(entry, defaults)
        return entry
    }

    private fun writeCustom(list: List<CatalogEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(AnalysisCatalog.encodeEntry(it.copy(custom = true))) }
        prefs.edit().putString(KEY_CUSTOM, arr.toString()).apply()
        bump()
    }

    private fun currentOverridesObject(): JSONObject {
        val raw = prefs.getString(KEY_OVERRIDES, null)
        return if (raw.isNullOrBlank()) JSONObject() else runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    companion object {
        private const val PREFS = "self_analysis"
        private const val KEY_CLEAN_LONG = "clean_day_long"
        private const val KEY_OVERRIDES = "catalog_overrides"
        private const val KEY_CUSTOM = "catalog_custom"
        private const val KEY_REMOTE_AT = "catalog_remote_at"
        private const val KEY_DIRTY = "catalog_dirty"
        private const val REMOTE_FILE = "analysis-catalog-remote.json"
    }
}
