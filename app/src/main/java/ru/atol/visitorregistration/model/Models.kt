package ru.atol.visitorregistration.model

import java.util.UUID

enum class VisitorType(val title: String) {
    PARTNER("Партнёр"),
    EMPLOYEE("Сотрудник"),
    GUEST("Гость")
}
enum class VisitorSource(val title: String) {
    PARTNER_FILE("Список партнёров"),
    EMPLOYEE_FILE("Список сотрудников"),
    MANUAL("Добавлен вручную")
}

data class Visitor(
    val id: String = UUID.randomUUID().toString(),
    val lastName: String,
    val firstName: String,
    val company: String = "",
    val position: String = "",
    val city: String = "",
    val type: VisitorType,
    val source: VisitorSource,
    val checkedInAt: Long? = null,
    val printCount: Int = 0
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}

data class PrinterConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Основной принтер",
    val host: String = "",
    val port: Int = 9100,
    val widthMm: Int = 58,
    val heightMm: Int = 40,
    val encoding: PrinterEncoding = PrinterEncoding.WINDOWS_1251,
    val fontName: String = "3",
    val isDefault: Boolean = false
)

enum class PrinterEncoding(val title: String, val codePage: String, val charsetName: String) {
    WINDOWS_1251("Windows-1251", "1251", "windows-1251"),
    CP866("CP866", "866", "IBM866"),
    ISO_8859_5("ISO-8859-5", "8859-5", "ISO-8859-5"),
    UTF_8("UTF-8", "UTF-8", "UTF-8")
}
