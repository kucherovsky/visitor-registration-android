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
    val widthMm: Int = 50,
    val heightMm: Int = 70,
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

enum class LabelRotation(val title: String, val degrees: Int) {
    DEG_0("0° · без поворота", 0),
    DEG_90("90° · по часовой стрелке", 90),
    DEG_180("180°", 180),
    DEG_270("270° · против часовой стрелки", 270)
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

const val REGULAR_LABEL_FONT = "FNR.TTF"
const val BOLD_LABEL_FONT = "FNB.TTF"

data class LabelLineConfig(
    val id: String = UUID.randomUUID().toString(),
    val attribute: LabelAttribute,
    val visible: Boolean = true,
    val fontName: String = REGULAR_LABEL_FONT,
    val fontSize: Int = 18,
    val style: LabelFontStyle = LabelFontStyle.NORMAL,
    val alignment: LabelTextAlignment = LabelTextAlignment.LEFT,
    val xMm: Float = 0f,
    val yMm: Float = 0f,
    val automaticPosition: Boolean = true
)

data class LabelTemplate(
    val kind: LabelTemplateKind,
    val widthMm: Int = 50,
    val heightMm: Int = 70,
    val resolution: PrinterResolution = PrinterResolution.DPI_203,
    val copies: Int = 1,
    val rotation: LabelRotation = LabelRotation.DEG_90,
    val alignment: LabelTextAlignment = LabelTextAlignment.LEFT,
    val lines: List<LabelLineConfig> = defaultLabelLines(kind)
) {
    val layoutWidthMm: Int
        get() = if (rotation == LabelRotation.DEG_90 || rotation == LabelRotation.DEG_270) heightMm else widthMm
    val layoutHeightMm: Int
        get() = if (rotation == LabelRotation.DEG_90 || rotation == LabelRotation.DEG_270) widthMm else heightMm
}

fun defaultLabelTemplate(kind: LabelTemplateKind): LabelTemplate = LabelTemplate(kind = kind)

private fun defaultLabelLines(kind: LabelTemplateKind): List<LabelLineConfig> = when (kind) {
    LabelTemplateKind.VISITOR -> listOf(
        LabelLineConfig(attribute = LabelAttribute.FULL_NAME, fontName = BOLD_LABEL_FONT, fontSize = 22, yMm = 3f),
        LabelLineConfig(attribute = LabelAttribute.COMPANY, fontName = REGULAR_LABEL_FONT, fontSize = 12),
        LabelLineConfig(attribute = LabelAttribute.CITY, fontName = REGULAR_LABEL_FONT, fontSize = 12)
    )
    LabelTemplateKind.EMPLOYEE -> listOf(
        LabelLineConfig(attribute = LabelAttribute.FULL_NAME, fontName = BOLD_LABEL_FONT, fontSize = 22, yMm = 3f),
        LabelLineConfig(attribute = LabelAttribute.POSITION, fontName = REGULAR_LABEL_FONT, fontSize = 10),
        LabelLineConfig(attribute = LabelAttribute.COMPANY, fontName = BOLD_LABEL_FONT, fontSize = 16)
    )
}
