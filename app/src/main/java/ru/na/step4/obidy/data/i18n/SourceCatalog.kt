package ru.na.step4.obidy.data.i18n

import java.util.concurrent.ConcurrentHashMap

object SourceCatalog {
    private val sources = ConcurrentHashMap<String, String>()

    fun register(key: String, sourceRu: String) {
        if (key.isBlank() || sourceRu.isBlank()) return
        sources.putIfAbsent(key, sourceRu)
    }

    fun put(key: String, sourceRu: String) {
        if (key.isBlank()) return
        sources[key] = sourceRu
    }

    fun get(key: String): String? = sources[key]

    fun sourceHash(sourceRu: String): String = sourceRu.hashCode().toUInt().toString(16)

    fun all(): Map<String, String> = sources.toMap()

    fun keys(): Set<String> = sources.keys.toSet()
}
