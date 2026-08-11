# -*- coding: utf-8 -*-
"""Generate xlsx/csv importer and wire ListScreen."""
from pathlib import Path

ROOT = Path(r"d:/sites/step4obidy/app/src/main/java/ru/na/step4/obidy")
MOD = chr(77) + "odifier"


def esc(s: str) -> str:
    return "".join(f"\\u{ord(c):04x}" if ord(c) > 127 else c for c in s)


def w(rel, content):
    content = content.replace("UI_MODIFIER", MOD)
    (ROOT / rel).parent.mkdir(parents=True, exist_ok=True)
    (ROOT / rel).write_text(content, encoding="utf-8", newline="\n")
    print("wrote", rel)


# Ru strings
ru = (ROOT / "Ru.kt").read_text(encoding="utf-8")
extras = {
    "importCd": "Импорт таблицы обид",
    "importOk": "Импортировано: %1$d обид, %2$d ситуаций",
    "importEmpty": "В файле не найдено строк для импорта",
    "importError": "Не удалось прочитать файл. Нужен .xlsx или .csv с колонками «На кого/что» и «Что произошло».",
    "importing": "Импорт…",
}
for key, text in extras.items():
    if f"const val {key}" not in ru:
        ru = ru.replace(
            "    const val micPermissionNeeded",
            f'    const val {key} = "{esc(text)}"\n    const val micPermissionNeeded',
            1,
        )
        print("ru+", key)
(ROOT / "Ru.kt").write_text(ru, encoding="utf-8", newline="\n")

w(
    "data/import/TableSheetReader.kt",
    r'''package ru.na.step4.obidy.data.import

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Lightweight reader for simple single-sheet .xlsx (OOXML) and .csv tables.
 * Avoids Apache POI to keep the APK small.
 */
object TableSheetReader {

    fun read(bytes: ByteArray, fileNameHint: String = ""): List<List<String>> {
        val lower = fileNameHint.lowercase()
        return when {
            lower.endsWith(".csv") || looksLikeCsv(bytes) -> readCsv(bytes)
            lower.endsWith(".xlsx") || looksLikeZip(bytes) -> readXlsx(bytes)
            else -> {
                runCatching { readXlsx(bytes) }.getOrElse { readCsv(bytes) }
            }
        }
    }

    private fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun looksLikeCsv(bytes: ByteArray): Boolean {
        val head = bytes.take(200).toByteArray().toString(Charsets.UTF_8)
        return head.contains(';') || head.contains(',') || head.contains('\n')
    }

    fun readCsv(bytes: ByteArray): List<List<String>> {
        val text = decodeText(bytes)
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()
        val delimiter = detectDelimiter(lines.first())
        return lines.map { parseCsvLine(it, delimiter) }
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        }
        // try UTF-8 then Windows-1251 (common for Russian Excel CSV)
        val utf8 = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
        if (utf8 != null && !utf8.contains('\uFFFD')) return utf8
        return bytes.toString(Charset.forName("windows-1251"))
    }

    private fun detectDelimiter(header: String): Char {
        val semi = header.count { it == ';' }
        val comma = header.count { it == ',' }
        return if (semi >= comma) ';' else ','
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    out.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString().trim())
        return out
    }

    fun readXlsx(bytes: ByteArray): List<List<String>> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        val shared = entries.entries
            .firstOrNull { it.key.equals("xl/sharedStrings.xml", true) }
            ?.value
            ?.let { parseSharedStrings(it) }
            .orEmpty()

        val sheetEntry = entries.entries.firstOrNull {
            it.key.matches(Regex("""xl/worksheets/sheet\d+\.xml""", RegexOption.IGNORE_CASE))
        } ?: throw IllegalArgumentException("sheet xml missing")

        return parseSheet(sheetEntry.value, shared)
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val doc = parseXml(xml)
        val result = mutableListOf<String>()
        val sis = doc.getElementsByTagName("si")
        for (i in 0 until sis.length) {
            val si = sis.item(i) as Element
            result.add(collectText(si).trim())
        }
        return result
    }

    private fun parseSheet(xml: ByteArray, shared: List<String>): List<List<String>> {
        val doc = parseXml(xml)
        val rowsNode = doc.getElementsByTagName("row")
        val sparse = linkedMapOf<Int, MutableMap<Int, String>>()
        var maxCol = 0
        for (i in 0 until rowsNode.length) {
            val rowEl = rowsNode.item(i) as Element
            val rowIndex = rowEl.getAttribute("r").toIntOrNull() ?: (i + 1)
            val cells = rowEl.getElementsByTagName("c")
            val map = sparse.getOrPut(rowIndex) { mutableMapOf() }
            for (j in 0 until cells.length) {
                val cell = cells.item(j) as Element
                val ref = cell.getAttribute("r")
                val col = columnIndex(ref)
                maxCol = maxOf(maxCol, col)
                val type = cell.getAttribute("t")
                val raw = cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
                val value = when (type) {
                    "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                    "inlineStr" -> collectText(cell)
                    else -> raw
                }
                map[col] = value.trim()
            }
        }
        if (sparse.isEmpty()) return emptyList()
        val width = maxCol.coerceAtLeast(1)
        return sparse.toSortedMap().map { (_, cols) ->
            (1..width).map { c -> cols[c].orEmpty() }
        }
    }

    private fun columnIndex(ref: String): Int {
        var n = 0
        for (c in ref) {
            if (c in 'A'..'Z') n = n * 26 + (c - 'A' + 1)
            else if (c in 'a'..'z') n = n * 26 + (c - 'a' + 1)
            else break
        }
        return n.coerceAtLeast(1)
    }

    private fun parseXml(bytes: ByteArray) =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun collectText(node: Node): String {
        val sb = StringBuilder()
        fun walk(n: Node) {
            if (n.nodeType == Node.TEXT_NODE || n.nodeType == Node.CDATA_SECTION_NODE) {
                sb.append(n.nodeValue)
            }
            var child = n.firstChild
            while (child != null) {
                walk(child)
                child = child.nextSibling
            }
        }
        walk(node)
        return sb.toString()
    }
}
''',
)

w(
    "data/import/ResentmentTableImporter.kt",
    r'''package ru.na.step4.obidy.data.import

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
        // key: categoryId|target -> resentmentId
        val resentmentCache = mutableMapOf<String, Long>()

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

            val key = "${categoryId ?: 0}|${target.trim().lowercase()}"
            val resentmentId = resentmentCache.getOrPut(key) {
                resentments++
                repository.save(
                    Resentment(
                        categoryId = categoryId,
                        target = target.trim(),
                        notes = cell(row, mapping.notes),
                        isCompleted = parseCompleted(cell(row, mapping.status))
                    )
                )
            }.also { id ->
                // update completion/notes if later rows set them
                val notes = cell(row, mapping.notes)
                val completed = parseCompleted(cell(row, mapping.status))
                if (notes.isNotBlank() || completed) {
                    repository.getById(id)?.let { existing ->
                        repository.save(
                            existing.copy(
                                notes = existing.notes.ifBlank { notes },
                                isCompleted = existing.isCompleted || completed
                            )
                        )
                    }
                }
            }

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
''',
)

print("import classes ok")
