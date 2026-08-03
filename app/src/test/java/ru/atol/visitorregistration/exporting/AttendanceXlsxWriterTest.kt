package ru.atol.visitorregistration.exporting

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.atol.visitorregistration.importing.XlsxTableReader
import ru.atol.visitorregistration.model.Visitor
import ru.atol.visitorregistration.model.VisitorSource
import ru.atol.visitorregistration.model.VisitorType

class AttendanceXlsxWriterTest {
    @Test
    fun createsWorkbookReadableByImporter() {
        val visitor = Visitor(
            lastName = "Иванов",
            firstName = "Иван",
            company = "Компания & партнёры",
            position = "Менеджер",
            city = "Москва",
            type = VisitorType.PARTNER,
            source = VisitorSource.PARTNER_FILE,
            checkedInAt = 1_700_000_000_000,
            printCount = 2
        )
        val output = ByteArrayOutputStream()

        AttendanceXlsxWriter().write(output, listOf(visitor))
        val rows = XlsxTableReader().read(ByteArrayInputStream(output.toByteArray()))

        assertEquals("Фамилия", rows[0][0])
        assertEquals("Иванов", rows[1][0])
        assertEquals("Компания & партнёры", rows[1][2])
        assertEquals("2", rows[1][8])
    }
}
