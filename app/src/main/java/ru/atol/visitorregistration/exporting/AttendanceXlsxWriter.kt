package ru.atol.visitorregistration.exporting

import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import ru.atol.visitorregistration.model.Visitor

class AttendanceXlsxWriter {
    fun write(output: OutputStream, visitors: List<Visitor>) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.text("[Content_Types].xml", contentTypes)
            zip.text("_rels/.rels", rootRelationships)
            zip.text("xl/workbook.xml", workbook)
            zip.text("xl/_rels/workbook.xml.rels", workbookRelationships)
            zip.text("xl/styles.xml", styles)
            zip.text("xl/worksheets/sheet1.xml", worksheet(visitors))
        }
    }

    private fun worksheet(visitors: List<Visitor>): String {
        val headers = listOf(
            "Фамилия", "Имя", "Компания", "Должность", "Город",
            "Тип", "Источник", "Время регистрации", "Печатей"
        )
        val rows = visitors.sortedBy { it.checkedInAt ?: Long.MAX_VALUE }.map { visitor ->
            listOf(
                visitor.lastName,
                visitor.firstName,
                visitor.company,
                visitor.position,
                visitor.city,
                visitor.type.title,
                visitor.source.title,
                visitor.checkedInAt?.let(::formatTimestamp).orEmpty(),
                visitor.printCount.toString()
            )
        }
        val lastRow = rows.size + 1
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
            append("<cols>")
            listOf(18, 18, 28, 34, 20, 16, 24, 22, 12).forEachIndexed { index, width ->
                append("<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"$width\" customWidth=\"1\"/>")
            }
            append("</cols><sheetData>")
            append(rowXml(1, headers, header = true))
            rows.forEachIndexed { index, values -> append(rowXml(index + 2, values, header = false)) }
            append("</sheetData>")
            append("<autoFilter ref=\"A1:I$lastRow\"/>")
            append("</worksheet>")
        }
    }

    private fun rowXml(rowNumber: Int, values: List<String>, header: Boolean): String = buildString {
        append("<row r=\"$rowNumber\">")
        values.forEachIndexed { index, value ->
            val reference = "${columnName(index)}$rowNumber"
            val style = if (header) " s=\"1\"" else ""
            append("<c r=\"$reference\" t=\"inlineStr\"$style><is><t xml:space=\"preserve\">")
            append(value.xmlSafe())
            append("</t></is></c>")
        }
        append("</row>")
    }

    private fun columnName(index: Int): String {
        var value = index + 1
        return buildString {
            while (value > 0) {
                insert(0, ('A'.code + (value - 1) % 26).toChar())
                value = (value - 1) / 26
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String = DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timestamp))

    private fun String.xmlSafe(): String = filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun ZipOutputStream.text(path: String, value: String) {
        putNextEntry(ZipEntry(path))
        write(value.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private companion object {
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
            </Types>
        """.trimIndent()
        val rootRelationships = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent()
        val workbook = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets><sheet name="Пришедшие" sheetId="1" r:id="rId1"/></sheets>
            </workbook>
        """.trimIndent()
        val workbookRelationships = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>
        """.trimIndent()
        val styles = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <fonts count="2"><font><sz val="11"/><name val="Arial"/></font><font><b/><sz val="11"/><name val="Arial"/></font></fonts>
              <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
              <borders count="1"><border/></borders>
              <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
              <cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>
            </styleSheet>
        """.trimIndent()
    }
}
