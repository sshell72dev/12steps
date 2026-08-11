package ru.na.step4.obidy.data

/**
 * Search helpers for resentment targets: phrase and/or word match, A–Z order.
 */
object ResentmentSearch {
    fun filterTargets(query: String, names: Iterable<String>): List<String> {
        val sorted = names
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        val q = query.trim()
        if (q.isEmpty()) return sorted
        val phrase = q.lowercase()
        val tokens = tokenize(q)
        return sorted.filter { name ->
            val lower = name.lowercase()
            lower.contains(phrase) || tokens.any { token -> lower.contains(token) }
        }
    }

    fun matchesResentment(query: String, target: String, preview: String = ""): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val hay = "$target $preview".lowercase()
        val phrase = q.lowercase()
        if (hay.contains(phrase)) return true
        return tokenize(q).any { token -> hay.contains(token) }
    }

    fun sortItemsByTarget(items: List<ResentmentListItem>): List<ResentmentListItem> =
        items.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) {
                it.resentment.target.ifBlank { "\uFFFF" }
            }
        )

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
}
