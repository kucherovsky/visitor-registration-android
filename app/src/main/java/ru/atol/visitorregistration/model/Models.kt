package ru.atol.visitorregistration.model

import java.util.UUID

enum class VisitorType(val title: String) {
    PARTNER("Посетитель"),
    EMPLOYEE("Сотрудник"),
    GUEST("Посетитель")
}
enum class VisitorSource(val title: String) {
    PARTNER_FILE("Список посетителей"),
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
    val fullName: String get() = listOf(lastName, firstName).filter { it.isNotBlank() }.joinToString(" ")
}

data class PrinterConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Основной принтер",
    val host: String = "",
    val port: Int = 9100,
    val widthMm: Int = 70,
    val heightMm: Int = 50,
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

enum class LabelTemplateKind(val title: String) {
    VISITOR("Посетители"),
    EMPLOYEE("Сотрудники")
}

enum class PrinterResolution(val title: String, val dpi: Int) {
    DPI_203("203 dpi", 203),
    DPI_300("300 dpi", 300)
}

enum class LabelAttribute(val title: String) {
    FULL_NAME("Фамилия и имя"),
    POSITION("Должность"),
    COMPANY("Компания"),
    CITY("Город"),
    VISITOR_TYPE("Тип посетителя"),
    REGISTRATION_DATE("Дата регистрации")
}

enum class LabelFontStyle(val title: String) {
    NORMAL("Обычный"),
    BOLD("Жирный"),
    ITALIC("Курсив"),
    UNDERLINE("Подчёркнутый")
}

enum class LabelTextAlignment(val title: String) {
    LEFT("По левому краю"),
    CENTER("По центру"),
    RIGHT("По правому краю")
}

data class LabelLineConfig(
    val id: String = UUID.randomUUID().toString(),
    val attribute: LabelAttribute,
    val visible: Boolean = true,
    val fontName: String = "3",
    val fontSize: Int = 18,
    val style: LabelFontStyle = LabelFontStyle.NORMAL,
    val alignment: LabelTextAlignment = LabelTextAlignment.LEFT,
    val xMm: Float = 3.5f,
    val yMm: Float = 3f,
    val automaticPosition: Boolean = true
)

data class LabelTemplate(
    val kind: LabelTemplateKind,
    val widthMm: Int = 70,
    val heightMm: Int = 50,
    val resolution: PrinterResolution = PrinterResolution.DPI_203,
    val lines: List<LabelLineConfig> = defaultLabelLines(kind)
)

fun defaultLabelTemplate(kind: LabelTemplateKind): LabelTemplate = LabelTemplate(kind = kind)

private fun defaultLabelLines(kind: LabelTemplateKind): List<LabelLineConfig> = when (kind) {
    LabelTemplateKind.VISITOR -> listOf(
        LabelLineConfig(attribute = LabelAttribute.FULL_NAME, fontSize = 28, style = LabelFontStyle.BOLD),
        LabelLineConfig(attribute = LabelAttribute.COMPANY, fontSize = 19),
        LabelLineConfig(attribute = LabelAttribute.CITY, fontSize = 16)
    )
    LabelTemplateKind.EMPLOYEE -> listOf(
        LabelLineConfig(attribute = LabelAttribute.FULL_NAME, fontSize = 28, style = LabelFontStyle.BOLD),
        LabelLineConfig(attribute = LabelAttribute.POSITION, fontSize = 18),
        LabelLineConfig(attribute = LabelAttribute.COMPANY, fontSize = 16)
    )
}
