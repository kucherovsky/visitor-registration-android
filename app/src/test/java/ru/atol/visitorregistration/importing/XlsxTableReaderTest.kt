package ru.atol.visitorregistration.importing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class XlsxTableReaderTest {
    @Test
    fun readsSharedStringsAndEmptyColumns() {
        val sharedStrings = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <si><t>Фамилия</t></si><si><t>Имя</t></si><si><t>Иванов</t></si><si><t>Иван</t></si>
            </sst>
        """.trimIndent()
        val sheet = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
              <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
              <row r="2"><c r="A2" t="s"><v>2</v></c><c r="C2" t="s"><v>3</v></c></row>
            </sheetData></worksheet>
        """.trimIndent()
        val bytes = xlsx(sharedStrings, sheet)

        val rows = XlsxTableReader().read(ByteArrayInputStream(bytes))

        assertEquals(listOf("Фамилия", "Имя"), rows[0])
        assertEquals(listOf("Иванов", "", "Иван"), rows[1])
    }

    @Test
    fun rejectsDoctypeBeforeXmlParserRuns() {
        val sharedStrings = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE sst [<!ENTITY external SYSTEM "file:///etc/passwd">]>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>&external;</t></si></sst>
        """.trimIndent()
        val sheet = """
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData/></worksheet>
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            XlsxTableReader().read(ByteArrayInputStream(xlsx(sharedStrings, sheet)))
        }
    }

    private fun xlsx(sharedStrings: String, sheet: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write(sharedStrings.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheet.toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
