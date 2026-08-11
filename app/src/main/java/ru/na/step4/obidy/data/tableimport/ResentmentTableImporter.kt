package ru.na.step4.obidy.data.tableimport

import ru.na.step4.obidy.data.Category
import ru.na.step4.obidy.data.Resentment
import ru.na.step4.obidy.data.ResentmentRepository
import ru.na.step4.obidy.data.Situation

data class ImportResult(
    val resentmentCount: Int,
    val situationCount: Int,
    val skippedRows: Int
)

/**
 * Imports "таблица обид" style tables:
 * Категория | На кого/что | Что произошло | Причина обиды | Чувства | Статус | Заметки
 */
class ResentmentTableImporter(
    private val repository: ResentmentRepository
) {
    suspend fun importTable(rows: List<List<String>>): ImportResult {
        if (rows.isEmpty()) return ImportResult(0, 0, 0)
        val header = rows.first().map { normalizeHeader(it) }
        val mapping = ColumnMapping.from(header)
        val dataRows = rows.drop(1)
        if (mapping.target < 0 && mapping.what < 0) {
            // maybe no header — treat first row as data with fixed Sergey layout
            return importWithFixedLayout(rows)
        }

        var resentments = 0
        var situations = 0
        var skipped = 0
        val categoryCache = mutableMapOf<String, Long>()
        repository.getCategories().forEach {
            categoryCache[it.name.trim().lowercase()] = it.id
        }

        // One Excel row = one resentment (table inventory rows stay 1:1).
        for (row in dataRows) {
            val target = cell(row, mapping.target)
            val what = cell(row, mapping.what)
            if (target.isBlank() && what.isBlank()) {
                skipped++
                continue
            }
            val categoryName = cell(row, mapping.category)
            val categoryId = if (categoryName.isBlank()) null
            else ensureCategory(categoryName, categoryCache)

            resentments++
            val resentmentId = repository.save(
                Resentment(
                    categoryId = categoryId,
                    target = target.trim(),
                    notes = cell(row, mapping.notes),
                    isCompleted = parseCompleted(cell(row, mapping.status))
                )
            )

            val types = splitTypes(cell(row, mapping.cause))
            val felt = cell(row, mapping.felt)
            val situationId = repository.saveSituation(
                Situation(
                    resentmentId = resentmentId,
                    title = what.trim().take(80),
                    whatHappened = what.trim(),
                    iFelt = felt
                )
            )
            situations++
            types.forEach { typeName ->
                val typeId = repository.addType(resentmentId, typeName)
                if (typeId > 0) repository.linkSituationToType(situationId, typeId)
            }
        }
        return ImportResult(resentments, situations, skipped)
    }

    private suspend fun importWithFixedLayout(rows: List<List<String>>): ImportResult {
        // №, Категория, На кого/что, Что произошло, Причина обиды, Чувства, Статус, Заметки
        val mapped = listOf(
            listOf("№", "Категория", "На кого/что", "Что произошло", "Причина обиды", "Чувства", "Статус", "Заметки")
        ) + rows
        return importTable(mapped)
    }

    private suspend fun ensureCategory(
        name: String,
        cache: MutableMap<String, Long>
    ): Long {
        val key = name.trim().lowercase()
        cache[key]?.let { return it }
        val id = repository.saveCategory(Category(name = name.trim()))
        cache[key] = id
        return id
    }

    private data class ColumnMapping(
        val category: Int,
        val target: Int,
        val what: Int,
        val cause: Int,
        val felt: Int,
        val status: Int,
        val notes: Int
    ) {
        companion object {
            fun from(header: List<String>): ColumnMapping {
                fun find(vararg keys: String): Int =
                    header.indexOfFirst { h -> keys.any { key -> h.contains(key) } }

                return ColumnMapping(
                    category = find("категор"),
                    target = find("на кого", "кому", "чему", "обижен"),
                    what = find("что произош", "ситуац"),
                    cause = find("причин", "тип"),
                    felt = find("чувств"),
                    status = find("статус", "проработ"),
                    notes = find("заметк", "коммент")
                )
            }
        }
    }

    private fun normalizeHeader(value: String): String =
        value.trim().lowercase().replace('\u00a0', ' ')

    private fun cell(row: List<String>, index: Int): String =
        if (index in row.indices) row[index].trim() else ""

    private fun splitTypes(raw: String): List<String> =
        raw.split(',', ';', '/', '|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun parseCompleted(status: String): Boolean {
        val s = status.trim().lowercase()
        if (s.isEmpty()) return false
        if (s.contains("не проработ")) return false
        return s.contains("проработ") || s.contains("готово") || s == "да" || s == "done"
    }
}
