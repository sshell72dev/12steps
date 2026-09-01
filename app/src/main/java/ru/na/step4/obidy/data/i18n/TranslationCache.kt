package ru.na.step4.obidy.data.i18n

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

data class CachedTranslation(
    val text: String,
    val sourceHash: String
)

class TranslationCache(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "i18n").also { it.mkdirs() }
    private val memory = ConcurrentHashMap<String, ConcurrentHashMap<String, CachedTranslation>>()

    fun get(lang: String, key: String, sourceRu: String): String? {
        val normalized = LocaleHelper.normalize(lang)
        if (LocaleHelper.isRussian(normalized)) return sourceRu
        val hash = SourceCatalog.sourceHash(sourceRu)
        val entry = memory.getOrPut(normalized) { loadLang(normalized) }[key] ?: return null
        return if (entry.sourceHash == hash) entry.text else null
    }

    fun putAll(lang: String, items: Map<String, CachedTranslation>) {
        if (items.isEmpty()) return
        val normalized = LocaleHelper.normalize(lang)
        val map = memory.getOrPut(normalized) { loadLang(normalized) }
        map.putAll(items)
        persist(normalized, map)
    }

    fun hasValid(lang: String, key: String, sourceRu: String): Boolean =
        get(lang, key, sourceRu) != null

    private fun loadLang(lang: String): ConcurrentHashMap<String, CachedTranslation> {
        val file = fileFor(lang)
        val out = ConcurrentHashMap<String, CachedTranslation>()
        if (!file.exists()) return out
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                val text = obj.optString("text")
                val hash = obj.optString("sourceHash")
                if (text.isNotBlank() && hash.isNotBlank()) {
                    out[key] = CachedTranslation(text, hash)
                }
            }
            out
        } catch (_: Exception) {
            ConcurrentHashMap()
        }
    }

    private fun persist(lang: String, map: Map<String, CachedTranslation>) {
        val root = JSONObject()
        map.forEach { (key, value) ->
            root.put(
                key,
                JSONObject()
                    .put("text", value.text)
                    .put("sourceHash", value.sourceHash)
            )
        }
        fileFor(lang).writeText(root.toString(), Charsets.UTF_8)
    }

    private fun fileFor(lang: String): File {
        val safe = lang.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(dir, "$safe.json")
    }
}
