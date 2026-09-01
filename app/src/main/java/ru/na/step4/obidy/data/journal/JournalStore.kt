package ru.na.step4.obidy.data.journal

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class JournalStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val mutex = Mutex()
    private val _entries = MutableStateFlow(readFile())
    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()

    fun entriesFor(nodeId: Int): List<JournalEntry> =
        _entries.value.filter { it.nodeId == nodeId }.sortedByDescending { it.createdAt }

    fun lastEntry(): JournalEntry? =
        _entries.value.maxByOrNull { it.createdAt }

    fun byId(id: String): JournalEntry? =
        _entries.value.find { it.id == id }

    suspend fun add(nodeId: Int, text: String): JournalEntry {
        val now = System.currentTimeMillis()
        val entry = JournalEntry(
            id = UUID.randomUUID().toString(),
            nodeId = nodeId,
            text = text.trim(),
            createdAt = now,
            updatedAt = now
        )
        mutex.withLock {
            val next = _entries.value + entry
            persist(next)
        }
        return entry
    }

    suspend fun update(id: String, text: String): JournalEntry? {
        var updated: JournalEntry? = null
        mutex.withLock {
            val next = _entries.value.map { entry ->
                if (entry.id != id) entry
                else entry.copy(text = text.trim(), updatedAt = System.currentTimeMillis())
                    .also { updated = it }
            }
            persist(next)
        }
        return updated
    }

    suspend fun delete(id: String) {
        mutex.withLock {
            persist(_entries.value.filterNot { it.id == id })
        }
    }

    fun exportJson(pathOf: (Int) -> String?): String {
        val arr = JSONArray()
        _entries.value.sortedBy { it.createdAt }.forEach { entry ->
            val obj = JSONObject()
                .put("id", entry.id)
                .put("nodeId", entry.nodeId)
                .put("text", entry.text)
                .put("createdAt", entry.createdAt)
                .put("updatedAt", entry.updatedAt)
            pathOf(entry.nodeId)?.let { obj.put("path", it) }
            arr.put(obj)
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("count", arr.length())
            .put("entries", arr)
            .toString(2)
    }

    suspend fun importFromJson(text: String): JournalFileImport {
        val incoming = parseEntries(text)
        if (incoming.isEmpty()) return JournalFileImport(0, 0)
        var added = 0
        var updated = 0
        mutex.withLock {
            val byId = _entries.value.associateBy { it.id }.toMutableMap()
            incoming.forEach { entry ->
                if (byId.containsKey(entry.id)) updated++ else added++
                byId[entry.id] = entry
            }
            persist(byId.values.sortedBy { it.createdAt })
        }
        return JournalFileImport(added = added, updated = updated)
    }

    private suspend fun persist(list: List<JournalEntry>) {
        _entries.value = list
        withContext(Dispatchers.IO) {
            val arr = JSONArray()
            list.forEach { entry ->
                arr.put(
                    JSONObject()
                        .put("id", entry.id)
                        .put("nodeId", entry.nodeId)
                        .put("text", entry.text)
                        .put("createdAt", entry.createdAt)
                        .put("updatedAt", entry.updatedAt)
                )
            }
            file.writeText(JSONObject().put("entries", arr).toString(), Charsets.UTF_8)
        }
    }

    private fun readFile(): List<JournalEntry> {
        if (!file.exists()) return emptyList()
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("entries") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                JournalEntry(
                    id = o.getString("id"),
                    nodeId = o.getInt("nodeId"),
                    text = o.optString("text"),
                    createdAt = o.optLong("createdAt"),
                    updatedAt = o.optLong("updatedAt", o.optLong("createdAt"))
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val FILE_NAME = "journal-entries.json"
        const val FORMAT = "steps12-journal"

        fun parseEntries(text: String): List<JournalEntry> {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return emptyList()
            val arr = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                else -> {
                    val root = JSONObject(trimmed)
                    root.optJSONArray("entries")
                        ?: error("no entries")
                }
            }
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val nodeId = o.optInt("nodeId", o.optInt("node_id", 0))
                val rawText = o.optString("text")
                if (nodeId <= 0 || rawText.isBlank()) return@mapNotNull null
                val created = o.optLong("createdAt", o.optLong("created_at", System.currentTimeMillis()))
                JournalEntry(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    nodeId = nodeId,
                    text = rawText,
                    createdAt = created,
                    updatedAt = o.optLong("updatedAt", o.optLong("updated_at", created))
                )
            }
        }
    }
}

data class JournalFileImport(
    val added: Int,
    val updated: Int
) {
    val total: Int get() = added + updated
}

class JournalPrefs(
    context: Context,
    val profile: ru.na.step4.obidy.data.profile.ProfileStore =
        ru.na.step4.obidy.data.profile.ProfileStore(context)
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var name: String
        get() = profile.name
        set(value) {
            profile.name = value
        }

    var registered: Boolean
        get() = prefs.getBoolean(KEY_REGISTERED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_REGISTERED, value).apply()
        }

    var problems: Set<String>
        get() = profile.problems
        set(value) {
            profile.problems = value
        }

    var currentId: Int
        get() = prefs.getInt(KEY_CURRENT, 0)
        set(value) {
            prefs.edit().putInt(KEY_CURRENT, value).apply()
        }

    var personality: String
        get() = profile.personality
        set(value) {
            profile.personality = value
        }

    var personalityEnabled: Boolean
        get() = profile.personalityEnabled
        set(value) {
            profile.personalityEnabled = value
        }

    var isPro: Boolean
        get() = prefs.getBoolean(KEY_PRO, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PRO, value).apply()
        }

    var isAdmin: Boolean
        get() = prefs.getBoolean(KEY_ADMIN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ADMIN, value).apply()
        }

    var adminCode: String
        get() = prefs.getString(KEY_ADMIN_CODE, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_ADMIN_CODE, value).apply()
        }

    var deviceId: String
        get() {
            val stored = prefs.getString(KEY_DEVICE, "").orEmpty()
            if (stored.isNotBlank()) return stored
            val created = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE, created).apply()
            return created
        }
        set(value) {
            prefs.edit().putString(KEY_DEVICE, value).apply()
        }

    var notesSyncedAt: String
        get() = prefs.getString(KEY_NOTES_SYNC, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_NOTES_SYNC, value).apply()
        }

    var analysesSyncedAt: String
        get() = prefs.getString(KEY_ANALYSES_SYNC, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_ANALYSES_SYNC, value).apply()
        }

    var draft: String
        get() = prefs.getString(KEY_DRAFT, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_DRAFT, value).apply()
        }

    var splitFields: Boolean
        get() = prefs.getBoolean(KEY_SPLIT_FIELDS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SPLIT_FIELDS, value).apply()
        }

    var fields: List<JournalFieldSpec>
        get() = JournalFields.decodeFields(prefs.getString(KEY_FIELDS, null))
        set(value) {
            prefs.edit().putString(KEY_FIELDS, JournalFields.encodeFields(value)).apply()
        }

    var fieldValues: Map<String, String>
        get() = JournalFields.decodeValues(prefs.getString(KEY_FIELD_VALUES, null))
        set(value) {
            prefs.edit().putString(KEY_FIELD_VALUES, JournalFields.encodeValues(value)).apply()
        }

    var editingId: String
        get() = prefs.getString(KEY_EDITING, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_EDITING, value).apply()
        }

    fun questionnaireAnswers(): Map<String, String> = profile.answers()

    fun putQuestionnaireAnswer(id: String, value: String) {
        profile.putAnswer(id, value)
    }

    fun skippedQuestions(): Set<String> = profile.skippedQuestions()

    fun skipQuestion(id: String) {
        profile.skipQuestion(id)
    }

    fun cachedHelp(nodeId: Int): String? =
        prefs.getString(cacheKey(nodeId), null)

    fun putCachedHelp(nodeId: Int, text: String) {
        prefs.edit().putString(cacheKey(nodeId), text).apply()
    }

    fun remainingAiToday(): Int {
        if (isAdmin) return Int.MAX_VALUE
        refreshDayIfNeeded()
        return (DAILY_LIMIT - prefs.getInt(KEY_AI_COUNT, 0)).coerceAtLeast(0)
    }

    fun canUseAi(): Boolean = isAdmin || remainingAiToday() > 0

    fun consumeAi() {
        if (isAdmin) return
        refreshDayIfNeeded()
        prefs.edit().putInt(KEY_AI_COUNT, prefs.getInt(KEY_AI_COUNT, 0) + 1).apply()
    }

    private fun refreshDayIfNeeded() {
        val today = todayStamp()
        if (prefs.getString(KEY_AI_DAY, "") != today) {
            prefs.edit()
                .putString(KEY_AI_DAY, today)
                .putInt(KEY_AI_COUNT, 0)
                .apply()
        }
    }

    private fun todayStamp(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH) + 1}-${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    }

    private fun cacheKey(nodeId: Int) = "help_$nodeId"

    companion object {
        private const val PREFS = "journal_local"
        private const val KEY_REGISTERED = "registered"
        private const val KEY_CURRENT = "current_id"
        private const val KEY_PRO = "pro"
        private const val KEY_ADMIN = "admin"
        private const val KEY_ADMIN_CODE = "admin_code"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_NOTES_SYNC = "notes_synced_at"
        private const val KEY_ANALYSES_SYNC = "analyses_synced_at"
        private const val KEY_DRAFT = "entry_draft"
        private const val KEY_SPLIT_FIELDS = "entry_split_fields"
        private const val KEY_FIELDS = "entry_fields"
        private const val KEY_FIELD_VALUES = "entry_field_values"
        private const val KEY_EDITING = "entry_editing_id"
        private const val KEY_AI_DAY = "ai_day"
        private const val KEY_AI_COUNT = "ai_count"
        const val DAILY_LIMIT = 3
    }
}
