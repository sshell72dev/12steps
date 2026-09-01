package ru.na.step4.obidy.data.life

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

class LifeBoardStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val mutex = Mutex()
    private val _items = MutableStateFlow(readFile())
    val items: StateFlow<List<LifeItem>> = _items.asStateFlow()

    fun ofKind(kind: String): List<LifeItem> =
        _items.value.filter { it.kind == LifeKind.normalize(kind) }

    fun byId(id: String): LifeItem? = _items.value.find { it.id == id }

    fun activeGoals(): List<LifeItem> =
        _items.value
            .filter { it.kind == LifeKind.GOAL && it.status == LifeStatus.IN_PROGRESS }
            .sortedBy { it.createdAt.takeIf { t -> t > 0L } ?: it.updatedAt }

    fun goalsPromptBlock(): String? {
        val goals = activeGoals()
        if (goals.isEmpty()) return null
        return buildString {
            appendLine("Цели пользователя (в работе — учитывай, когда уместно):")
            goals.take(12).forEach { item ->
                append("- ${item.title.trim()}")
                if (item.body.isNotBlank()) append(": ${item.body.trim()}")
                appendLine()
            }
        }.trim().takeIf { it.isNotBlank() }
    }

    fun goalsFingerprint(): String =
        activeGoals().joinToString("|") { "${it.id}:${it.title}:${it.body}" }

    suspend fun upsert(
        id: String?,
        kind: String,
        title: String,
        body: String,
        status: String,
        dueAt: Long?,
        sourceId: String = ""
    ): LifeItem? {
        val split = splitText(title, body)
        if (split.first.isBlank()) return null
        val now = System.currentTimeMillis()
        val normalizedKind = LifeKind.normalize(kind)
        val normalizedStatus = LifeStatus.normalize(status)
        val eventDue = if (normalizedKind == LifeKind.EVENT) dueAt ?: startOfToday() else dueAt
        var saved: LifeItem? = null
        mutex.withLock {
            val current = _items.value.toMutableList()
            val existing = id?.let { key -> current.indexOfFirst { it.id == key } } ?: -1
            if (existing >= 0) {
                val prev = current[existing]
                saved = prev.copy(
                    title = split.first,
                    body = split.second,
                    status = normalizedStatus,
                    dueAt = eventDue,
                    updatedAt = now,
                    sourceId = sourceId.ifBlank { prev.sourceId }
                )
                current[existing] = saved!!
            } else {
                saved = LifeItem(
                    id = UUID.randomUUID().toString(),
                    kind = normalizedKind,
                    title = split.first,
                    body = split.second,
                    status = normalizedStatus,
                    dueAt = eventDue,
                    createdAt = now,
                    updatedAt = now,
                    sourceId = sourceId
                )
                current.add(saved!!)
            }
            persist(current)
        }
        return saved
    }

    suspend fun addIdea(text: String, sourceId: String = ""): LifeItem? {
        val split = splitText("", text)
        if (split.first.isBlank()) return null
        mutex.withLock {
            val current = _items.value
            val duplicate = current.firstOrNull { item ->
                item.kind == LifeKind.IDEA && sameIdea(item, split.first, split.second, sourceId)
            }
            if (duplicate != null) {
                if (sourceId.isNotBlank() && duplicate.sourceId.isBlank()) {
                    val patched = current.map {
                        if (it.id == duplicate.id) it.copy(sourceId = sourceId) else it
                    }
                    persist(patched)
                    return patched.first { it.id == duplicate.id }
                }
                return duplicate
            }
        }
        return upsert(
            id = null,
            kind = LifeKind.IDEA,
            title = split.first,
            body = split.second,
            status = LifeStatus.IN_PROGRESS,
            dueAt = null,
            sourceId = sourceId
        )
    }

    suspend fun setStatus(id: String, status: String) {
        mutex.withLock {
            val next = _items.value.map { item ->
                if (item.id != id) item
                else item.copy(
                    status = LifeStatus.normalize(status),
                    updatedAt = System.currentTimeMillis()
                )
            }
            persist(next)
        }
    }

    suspend fun delete(id: String) {
        mutex.withLock {
            persist(_items.value.filterNot { it.id == id })
        }
    }

    private suspend fun persist(next: List<LifeItem>) {
        _items.value = next
        withContext(Dispatchers.IO) { writeFile(next) }
    }

    private fun readFile(): List<LifeItem> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("items") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                LifeItem(
                    id = obj.optString("id").ifBlank { return@mapNotNull null },
                    kind = LifeKind.normalize(obj.optString("kind")),
                    title = obj.optString("title"),
                    body = obj.optString("body"),
                    status = LifeStatus.normalize(obj.optString("status")),
                    dueAt = if (obj.has("dueAt") && !obj.isNull("dueAt")) obj.optLong("dueAt") else null,
                    createdAt = obj.optLong("createdAt"),
                    updatedAt = obj.optLong("updatedAt"),
                    sourceId = obj.optString("sourceId")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeFile(items: List<LifeItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("kind", item.kind)
                    .put("title", item.title)
                    .put("body", item.body)
                    .put("status", item.status)
                    .put("dueAt", item.dueAt ?: JSONObject.NULL)
                    .put("createdAt", item.createdAt)
                    .put("updatedAt", item.updatedAt)
                    .put("sourceId", item.sourceId)
            )
        }
        file.writeText(
            JSONObject()
                .put("format", FORMAT)
                .put("version", 1)
                .put("items", arr)
                .toString()
        )
    }

    companion object {
        private const val FILE_NAME = "life-board.json"
        private const val FORMAT = "life-board.v1"

        fun startOfToday(): Long {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        private fun splitText(title: String, body: String): Pair<String, String> {
            val trimmedTitle = title.trim()
            val trimmedBody = body.trim()
            if (trimmedTitle.isNotBlank()) return trimmedTitle.take(TITLE_LIMIT) to trimmedBody
            if (trimmedBody.isBlank()) return "" to ""
            val lines = trimmedBody.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            val head = lines.firstOrNull().orEmpty().take(TITLE_LIMIT)
            val rest = if (lines.size <= 1) "" else lines.drop(1).joinToString("\n")
            return head to rest
        }

        private fun sameIdea(item: LifeItem, title: String, body: String, sourceId: String): Boolean {
            if (sourceId.isNotBlank() && item.sourceId == sourceId) return true
            val left = fingerprint(item.title, item.body)
            val right = fingerprint(title, body)
            if (left.isBlank() || right.isBlank()) return false
            if (left == right) return true
            val prefix = minOf(left.length, right.length, 40)
            if (prefix < 24) return false
            return left.startsWith(right.take(prefix)) || right.startsWith(left.take(prefix))
        }

        private fun fingerprint(title: String, body: String): String =
            "$title\n$body".trim().replace(Regex("\\s+"), " ")

        private const val TITLE_LIMIT = 120
    }
}

object LifeBoardPrompts {
    fun merge(questionnaire: String?, goals: String?): String? {
        val parts = listOfNotNull(
            questionnaire?.trim()?.takeIf { it.isNotBlank() },
            goals?.trim()?.takeIf { it.isNotBlank() }
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }
}
