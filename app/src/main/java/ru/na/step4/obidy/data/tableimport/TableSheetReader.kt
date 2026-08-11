package ru.na.step4.obidy.data.tableimport

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lightweight table reader for .xlsx / .csv without Apache POI.
 * Supports sharedStrings and inlineStr (Google Sheets / LibreOffice export).
 */
object TableSheetReader {
    fun read(bytes: ByteArray, fileNameHint: String = ""): List<List<String>> {
        val lower = fileNameHint.lowercase()
        // ZIP/xlsx first: binary often contains 0x0A and would falsely match CSV.
        return when {
            looksLikeZip(bytes) || lower.endsWith(".xlsx") || lower.endsWith(".xlsm") ->
                readXlsx(bytes)
            lower.endsWith(".csv") || looksLikeCsv(bytes) ->
                readCsv(bytes)
            else ->
                error("Поддерживаются только .xlsx и .csv")
        }
    }

    private fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte()

    private fun looksLikeCsv(bytes: ByteArray): Boolean {
        if (looksLikeZip(bytes)) return false
        val head = bytes.take(256).toByteArray().toString(Charsets.UTF_8)
        return head.contains(';') || head.contains(',') || head.contains('\n')
    }

    private fun readCsv(bytes: ByteArray): List<List<String>> {
        val text = decodeText(bytes).replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val delim = if (lines.first().count { it == ';' } >= lines.first().count { it == ',' }) ';' else ','
        return lines.map { splitCsvLine(it, delim) }
    }

    private fun decodeText(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        if ('\uFFFD' !in utf8) return utf8
        return runCatching { bytes.toString(Charset.forName("windows-1251")) }.getOrDefault(utf8)
    }

    private fun splitCsvLine(line: String, delim: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delim && !inQuotes -> {
                    out += cur.toString()
                    cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val name = e.name.replace('\\', '/')
                entries[name] = zip.readBytes()
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        val shared = entries.entries
            .firstOrNull { it.key.equals("xl/sharedStrings.xml", ignoreCase = true) }
            ?.value
            ?.let { parseSharedStrings(it) }
            .orEmpty()
        val sheetBytes = entries.entries
            .firstOrNull {
                val n = it.key.lowercase()
                n.startsWith("xl/worksheets/sheet") && n.endsWith(".xml")
            }
            ?.value
            ?: error("В файле нет листа worksheet")
        return parseSheet(sheetBytes, shared)
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val root = parseXml(xml).documentElement ?: return emptyList()
        val out = mutableListOf<String>()
        for (si in childElements(root, "si")) {
            out += collectText(si)
        }
        return out
    }

    private fun parseSheet(xml: ByteArray, shared: List<String>): List<List<String>> {
        val root = parseXml(xml).documentElement
            ?: return emptyList()
        val sheetData = firstChild(root, "sheetData") ?: return emptyList()
        val rows = mutableListOf<List<String>>()
        for (rowEl in childElements(sheetData, "row")) {
            val cells = linkedMapOf<Int, String>()
            var maxCol = -1
            for (c in childElements(rowEl, "c")) {
                val ref = c.getAttribute("r")
                val col = columnIndex(ref)
                val type = c.getAttribute("t")
                val value = when (type) {
                    "inlineStr" -> {
                        val isEl = firstChild(c, "is")
                        if (isEl != null) collectText(isEl) else ""
                    }
                    "s" -> {
                        val idx = firstChild(c, "v")?.textContent?.trim()?.toIntOrNull() ?: -1
                        shared.getOrNull(idx).orEmpty()
                    }
                    else -> firstChild(c, "v")?.textContent.orEmpty()
                }
                cells[col] = value
                if (col > maxCol) maxCol = col
            }
            val width = maxOf(maxCol + 1, 1)
            rows += (0 until width).map { cells[it].orEmpty() }
        }
        return rows
    }

    private fun parseXml(bytes: ByteArray) =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun firstChild(parent: Element, local: String): Element? {
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && localName(n) == local) {
                return n as Element
            }
        }
        return null
    }

    private fun childElements(parent: Element, local: String): List<Element> {
        val out = mutableListOf<Element>()
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && localName(n) == local) {
                out += n as Element
            }
        }
        return out
    }

    private fun localName(node: Node): String {
        val local = node.localName
        if (!local.isNullOrEmpty()) return local
        val name = node.nodeName ?: return ""
        val i = name.indexOf(':')
        return if (i >= 0) name.substring(i + 1) else name
    }

    private fun collectText(el: Element): String {
        // Prefer explicit <t> nodes (shared string rich text / inlineStr)
        val tNodes = mutableListOf<Element>()
        fun findT(n: Node) {
            if (n.nodeType == Node.ELEMENT_NODE && localName(n) == "t") {
                tNodes += n as Element
                return
            }
            if (n.nodeType == Node.ELEMENT_NODE && localName(n) == "rPh") return
            val kids = n.childNodes
            for (i in 0 until kids.length) findT(kids.item(i))
        }
        findT(el)
        if (tNodes.isNotEmpty()) {
            return tNodes.joinToString("") { it.textContent.orEmpty() }
        }
        return el.textContent.orEmpty()
    }

    /** A1 -> 0, B12 -> 1, AA3 -> 26 */
    private fun columnIndex(ref: String): Int {
        var i = 0
        var col = 0
        while (i < ref.length && ref[i].isLetter()) {
            col = col * 26 + (ref[i].uppercaseChar() - 'A' + 1)
            i++
        }
        return (col - 1).coerceAtLeast(0)
    }
}
