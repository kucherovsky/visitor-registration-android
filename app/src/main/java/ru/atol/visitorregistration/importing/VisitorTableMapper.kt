package ru.atol.visitorregistration.importing

import java.nio.charset.StandardCharsets
import java.util.UUID
import ru.atol.visitorregistration.data.ImportResult
import ru.atol.visitorregistration.model.Visitor
import ru.atol.visitorregistration.model.VisitorSource
import ru.atol.visitorregistration.model.VisitorType

class VisitorTableMapper {
    fun map(rows: List<List<String>>, type: VisitorType): ImportResult {
        require(rows.isNotEmpty()) { "Excel-файл пуст" }
        val headerRow = rows.indexOfFirst { row -> row.any(String::isNotBlank) }
        require(headerRow >= 0) { "Excel-файл не содержит данных" }
        val headers = rows[headerRow].map(::normalizeHeader)
        val columns = headers.mapIndexed { index, header -> header to index }.toMap()
        val required = when (type) {
            VisitorType.PARTNER -> listOf("фамилия", "имя", "наименование компании", "город")
            VisitorType.EMPLOYEE -> listOf("фамилия", "имя", "должность", "компания")
            VisitorType.GUEST -> error("Гостевой список не импортируется")
        }
        val missing = required.filterNot(columns::containsKey)
        require(missing.isEmpty()) { "В файле отсутствуют столбцы: ${missing.joinToString()}" }

        var skipped = 0
        var emptyNames = 0
        var emptyCities = 0
        val visitors = mutableListOf<Visitor>()
        rows.drop(headerRow + 1).forEach { row ->
            if (row.all(String::isBlank)) return@forEach
            val lastName = value(row, columns, "фамилия")
            if (lastName.isBlank()) {
                skipped++
                return@forEach
            }
            val firstName = value(row, columns, "имя")
            if (firstName.isBlank()) emptyNames++
            val companyColumn = if (type == VisitorType.PARTNER) "наименование компании" else "компания"
            val company = value(row, columns, companyColumn)
            val position = if (type == VisitorType.EMPLOYEE) value(row, columns, "должность") else ""
            val city = if (type == VisitorType.PARTNER) value(row, columns, "город") else ""
            if (type == VisitorType.PARTNER && city.isBlank()) emptyCities++
            val source = if (type == VisitorType.PARTNER) VisitorSource.PARTNER_FILE else VisitorSource.EMPLOYEE_FILE
            visitors += Visitor(
                id = stableId(type, lastName, firstName, company, position, city),
                lastName = lastName,
                firstName = firstName,
                company = company,
                position = position,
                city = city,
                type = type,
                source = source
            )
        }

        val duplicateNames = visitors
            .groupingBy { "${it.lastName.lowercase()}|${it.firstName.lowercase()}" }
            .eachCount()
            .values
            .sumOf { count -> (count - 1).coerceAtLeast(0) }
        val warnings = buildList {
            if (skipped > 0) add("Пропущено строк без фамилии: $skipped")
            if (emptyNames > 0) add("Записей без имени: $emptyNames")
            if (emptyCities > 0) add("Записей без города: $emptyCities")
            if (duplicateNames > 0) add("Возможных тёзок или дублей: $duplicateNames")
        }
        return ImportResult(visitors = visitors, skipped = skipped, warnings = warnings)
    }

    private fun value(row: List<String>, columns: Map<String, Int>, header: String): String =
        row.getOrNull(requireNotNull(columns[header])).orEmpty().trim().replace(Regex("\\s+"), " ")

    private fun normalizeHeader(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun stableId(type: VisitorType, vararg values: String): String {
        val source = (listOf(type.name) + values.map { it.trim().lowercase() }).joinToString("|")
        return UUID.nameUUIDFromBytes(source.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
