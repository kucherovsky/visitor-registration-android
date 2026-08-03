package ru.atol.visitorregistration.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.atol.visitorregistration.model.VisitorSource
import ru.atol.visitorregistration.model.VisitorType

class VisitorTableMapperTest {
    private val mapper = VisitorTableMapper()

    @Test
    fun mapsPartnerFormatAndReportsWarnings() {
        val rows = listOf(
            listOf("Фамилия", "Имя", "Наименование компании", "Город"),
            listOf(" Иванов ", " Иван ", " Компания  А ", " Москва "),
            listOf("Петров", "", "Компания Б", ""),
            listOf("", "Без", "Фамилии", "Казань")
        )

        val result = mapper.map(rows, VisitorType.PARTNER)

        assertEquals(2, result.imported)
        assertEquals(1, result.skipped)
        assertEquals("Компания А", result.visitors.first().company)
        assertEquals(VisitorSource.PARTNER_FILE, result.visitors.first().source)
        assertTrue(result.warnings.contains("Записей без имени: 1"))
        assertTrue(result.warnings.contains("Записей без города: 1"))
    }

    @Test
    fun mapsEmployeeFormatAndKeepsStableId() {
        val rows = listOf(
            listOf("Фамилия", "Имя", "Должность", "Компания"),
            listOf("Смирнов", "Алексей", "Менеджер", "АТОЛ")
        )

        val first = mapper.map(rows, VisitorType.EMPLOYEE).visitors.single()
        val second = mapper.map(rows, VisitorType.EMPLOYEE).visitors.single()

        assertEquals(first.id, second.id)
        assertEquals("Менеджер", first.position)
        assertEquals(VisitorSource.EMPLOYEE_FILE, first.source)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongFormat() {
        mapper.map(listOf(listOf("Фамилия", "Имя")), VisitorType.EMPLOYEE)
    }
}
