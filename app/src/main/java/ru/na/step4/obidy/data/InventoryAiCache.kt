package ru.na.step4.obidy.data

import android.content.Context
import java.io.File
import org.json.JSONObject

data class CachedSituationAi(
    val insights: Map<String, InventoryFieldInsight> = emptyMap(),
    val fullAnalysis: String = "",
    /** Отпечаток входных данных, по которым сделан разбор: позволяет не тратить запрос повторно. */
    val sourceHash: Int = 0
)

class InventoryAiCache(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val lock = Any()

    fun get(situationId: Long): CachedSituationAi? {
        if (situationId <= 0L) return null
        return synchronized(lock) {
            val entry = read().optJSONObject(KEY_SITUATIONS)?.optJSONObject(situationId.toString())
                ?: return null
            val insights = parseInsights(entry.optJSONObject("insights"))
            val full = entry.optString("fullAnalysis").trim()
            if (insights.isEmpty() && full.isBlank()) null
            else CachedSituationAi(insights, full, entry.optInt("sourceHash", 0))
        }
    }

    fun save(
        situationId: Long,
        insights: Map<String, InventoryFieldInsight>,
        fullAnalysis: String = "",
        sourceHash: Int = 0
    ) {
        if (situationId <= 0L) return
        if (insights.isEmpty() && fullAnalysis.isBlank()) {
            clear(situationId)
            return
        }
        synchronized(lock) {
            val root = read()
            val all = root.optJSONObject(KEY_SITUATIONS) ?: JSONObject()
            val entry = JSONObject()
            if (fullAnalysis.isNotBlank()) entry.put("fullAnalysis", fullAnalysis)
            if (sourceHash != 0) entry.put("sourceHash", sourceHash)
            if (insights.isNotEmpty()) {
                val obj = JSONObject()
                insights.forEach { (key, insight) -> obj.put(key, insightToJson(insight)) }
                entry.put("insights", obj)
            }
            entry.put("updatedAt", System.currentTimeMillis())
            all.put(situationId.toString(), entry)
            root.put(KEY_SITUATIONS, all)
            write(root)
        }
    }

    fun mergeInsights(
        situationId: Long,
        newInsights: Map<String, InventoryFieldInsight>,
        fullAnalysis: String? = null
    ): CachedSituationAi {
        val existing = get(situationId)
        val merged = existing?.insights.orEmpty() + newInsights
        val full = when {
            fullAnalysis != null -> fullAnalysis
            else -> existing?.fullAnalysis.orEmpty()
        }
        val hash = existing?.sourceHash ?: 0
        save(situationId, merged, full, hash)
        return CachedSituationAi(merged, full, hash)
    }

    /** Углублённая проработка ситуации: вопросы, ответы и разборы по кругам. */
    fun deep(situationId: Long): InventoryDeepState {
        if (situationId <= 0L) return InventoryDeepState()
        return synchronized(lock) {
            InventoryDeepState.fromJson(
                read().optJSONObject(KEY_DEEPS)?.optJSONObject(situationId.toString())
            )
        }
    }

    fun saveDeep(situationId: Long, state: InventoryDeepState) {
        if (situationId <= 0L) return
        synchronized(lock) {
            val root = read()
            val all = root.optJSONObject(KEY_DEEPS) ?: JSONObject()
            if (state.isEmpty) {
                all.remove(situationId.toString())
            } else {
                all.put(situationId.toString(), InventoryDeepState.toJson(state))
            }
            root.put(KEY_DEEPS, all)
            write(root)
        }
    }

    fun clear(situationId: Long) {
        if (situationId <= 0L) return
        synchronized(lock) {
            val root = read()
            val all = root.optJSONObject(KEY_SITUATIONS) ?: return
            if (!all.has(situationId.toString())) return
            all.remove(situationId.toString())
            root.put(KEY_SITUATIONS, all)
            root.optJSONObject(KEY_DEEPS)?.let { deeps ->
                deeps.remove(situationId.toString())
                root.put(KEY_DEEPS, deeps)
            }
            write(root)
        }
    }

    private fun parseInsights(obj: JSONObject?): Map<String, InventoryFieldInsight> {
        if (obj == null) return emptyMap()
        val out = LinkedHashMap<String, InventoryFieldInsight>()
        obj.keys().forEach { key ->
            val item = obj.optJSONObject(key) ?: return@forEach
            val text = item.optString("text").trim()
            if (text.isBlank()) return@forEach
            val kind = runCatching {
                InventoryInsightKind.valueOf(item.optString("kind", InventoryInsightKind.DRAFT.name))
            }.getOrDefault(InventoryInsightKind.DRAFT)
            out[key] = InventoryFieldInsight(
                key = key,
                title = item.optString("title").ifBlank { QuestionFocus.titleOf(key) },
                kind = kind,
                text = text
            )
        }
        return out
    }

    private fun insightToJson(insight: InventoryFieldInsight): JSONObject =
        JSONObject()
            .put("key", insight.key)
            .put("title", insight.title)
            .put("kind", insight.kind.name)
            .put("text", insight.text)

    private fun read(): JSONObject {
        if (!file.exists()) return JSONObject()
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .getOrDefault(JSONObject())
    }

    private fun write(root: JSONObject) {
        file.writeText(root.toString(), Charsets.UTF_8)
    }

    companion object {
        private const val FILE_NAME = "inventory-ai-cache.json"
        private const val KEY_SITUATIONS = "situations"
        private const val KEY_DEEPS = "deeps"
    }
}
