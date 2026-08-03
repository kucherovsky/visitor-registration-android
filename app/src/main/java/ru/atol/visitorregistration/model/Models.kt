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
    val name: String = "Основной принтер",
    val host: String = "",
    val port: Int = 9100,
    val widthMm: Int = 58,
    val heightMm: Int = 40
)
