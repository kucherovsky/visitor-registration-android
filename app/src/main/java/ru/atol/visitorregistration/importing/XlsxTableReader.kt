package ru.atol.visitorregistration.importing

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class XlsxTableReader {
    fun read(input: InputStream): List<List<String>> {
        val entries = unzip(input)
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::readSharedStrings).orEmpty()
        val sheetName = entries.keys
            .filter { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .minByOrNull { it.removePrefix("xl/worksheets/sheet").removeSuffix(".xml").toIntOrNull() ?: Int.MAX_VALUE }
            ?: error("В Excel-файле не найден рабочий лист")
        return readSheet(requireNotNull(entries[sheetName]), sharedStrings)
    }

    private fun unzip(input: InputStream): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && (
                        entry.name == "xl/sharedStrings.xml" ||
                            entry.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml"))
                        )
                ) {
                    result[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }

    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val document = parseXml(bytes)
        val items = document.getElementsByTagNameNS("*", "si")
        return buildList(items.length) {
            for (index in 0 until items.length) {
                val item = items.item(index) as Element
                val texts = item.getElementsByTagNameNS("*", "t")
                add(buildString {
                    for (textIndex in 0 until texts.length) append(texts.item(textIndex).textContent)
                })
            }
        }
    }

    private fun readSheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val document = parseXml(bytes)
        val rows = document.getElementsByTagNameNS("*", "row")
        return buildList(rows.length) {
            for (rowIndex in 0 until rows.length) {
                val row = rows.item(rowIndex) as Element
                val cells = row.getElementsByTagNameNS("*", "c")
                val values = mutableMapOf<Int, String>()
                var maxColumn = -1
                for (cellIndex in 0 until cells.length) {
                    val cell = cells.item(cellIndex) as Element
                    val column = columnIndex(cell.getAttribute("r"))
                    if (column < 0) continue
                    maxColumn = maxOf(maxColumn, column)
                    values[column] = cellValue(cell, sharedStrings)
                }
                if (maxColumn >= 0) add(List(maxColumn + 1) { values[it].orEmpty() })
            }
        }
    }

    private fun cellValue(cell: Element, sharedStrings: List<String>): String {
        val type = cell.getAttribute("t")
        if (type == "inlineStr") {
            val texts = cell.getElementsByTagNameNS("*", "t")
            return buildString {
                for (index in 0 until texts.length) append(texts.item(index).textContent)
            }
        }
        val values = cell.getElementsByTagNameNS("*", "v")
        val raw = if (values.length > 0) values.item(0).textContent else ""
        return if (type == "s") sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
    }

    private fun columnIndex(reference: String): Int {
        val letters = reference.takeWhile(Char::isLetter)
        if (letters.isEmpty()) return -1
        return letters.fold(0) { value, char -> value * 26 + (char.uppercaseChar() - 'A' + 1) } - 1
    }

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
        require(!String(bytes, Charsets.UTF_8).contains("<!DOCTYPE", ignoreCase = true)) {
            "Excel-файл содержит запрещённое XML-описание"
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { isExpandEntityReferences = false }
            runCatching { isXIncludeAware = false }
        }
        // Набор поддерживаемых XML-функций отличается между Android и обычной JVM.
        // DOCTYPE проверяется выше напрямую, остальные ограничения включаются там, где они доступны.
        listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false
        ).forEach { (feature, enabled) -> runCatching { factory.setFeature(feature, enabled) } }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }
}
