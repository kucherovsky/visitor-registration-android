package ru.atol.visitorregistration.printing

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.floor
import kotlin.math.roundToInt
import ru.atol.visitorregistration.model.LabelAttribute
import ru.atol.visitorregistration.model.LabelFontStyle
import ru.atol.visitorregistration.model.LabelLineConfig
import ru.atol.visitorregistration.model.LabelTemplate
import ru.atol.visitorregistration.model.LabelTemplateKind
import ru.atol.visitorregistration.model.LabelTextAlignment
import ru.atol.visitorregistration.model.PrinterEncoding
import ru.atol.visitorregistration.model.Visitor
import ru.atol.visitorregistration.model.VisitorSource
import ru.atol.visitorregistration.model.VisitorType

data class PlacedLabelText(
    val lineId: String,
    val text: String,
    val xMm: Float,
    val yMm: Float,
    val fontName: String,
    val fontSize: Int,
    val style: LabelFontStyle,
    val alignment: LabelTextAlignment,
    val estimatedWidthMm: Float,
    val lineHeightMm: Float
)

class TsplTemplateEngine {
    fun sampleVisitor(kind: LabelTemplateKind): Visitor = when (kind) {
        LabelTemplateKind.VISITOR -> Visitor(
            lastName = "Иванов",
            firstName = "Иван",
            company = "Международная ассоциация технологических компаний",
            city = "Нижний Новгород",
            type = VisitorType.PARTNER,
            source = VisitorSource.PARTNER_FILE
        )
        LabelTemplateKind.EMPLOYEE -> Visitor(
            lastName = "Петрова",
            firstName = "Мария",
            company = "АТОЛ",
            position = "Руководитель направления корпоративных решений",
            type = VisitorType.EMPLOYEE,
            source = VisitorSource.EMPLOYEE_FILE
        )
    }

    fun applyAutomaticPositions(template: LabelTemplate): LabelTemplate {
        val firstPlacement = layout(template, sampleVisitor(template.kind)).groupBy(PlacedLabelText::lineId)
        return template.copy(lines = template.lines.map { line ->
            if (!line.automaticPosition) line else firstPlacement[line.id]?.firstOrNull()?.let { placed ->
                line.copy(xMm = placed.xMm, yMm = placed.yMm)
            } ?: line
        })
    }

    fun layout(template: LabelTemplate, visitor: Visitor): List<PlacedLabelText> =
        layoutValues(template) { attribute -> attributeValue(attribute, visitor) }

    fun templateText(template: LabelTemplate, encoding: PrinterEncoding = PrinterEncoding.WINDOWS_1251): String {
        val dotsPerMm = template.resolution.dpi / 25.4f
        val samplePlacements = layout(template, sampleVisitor(template.kind)).groupBy(PlacedLabelText::lineId)
        val commands = mutableListOf(
            "REM TARGET_DPI ${template.resolution.dpi}",
            "SIZE ${template.widthMm} mm,${template.heightMm} mm",
            "GAP 2 mm,0 mm",
            "DIRECTION 1",
            "CODEPAGE ${encoding.codePage}",
            "CLS"
        )
        template.lines.filter(LabelLineConfig::visible).forEach { line ->
            val placed = samplePlacements[line.id]?.firstOrNull() ?: return@forEach
            val x = (placed.xMm * dotsPerMm).roundToInt()
            val y = (placed.yMm * dotsPerMm).roundToInt()
            val multiplier = (placed.fontSize / 16f).roundToInt().coerceIn(1, 10)
            if (line.style == LabelFontStyle.BOLD) commands += "SETBOLD 1"
            if (line.style == LabelFontStyle.ITALIC) commands += "REM STYLE ITALIC"
            if (line.style == LabelFontStyle.UNDERLINE) commands += "REM STYLE UNDERLINE"
            commands += "TEXT $x,$y,\"${line.fontName.tsplFontSafe()}\",0,$multiplier,$multiplier,\"${placeholder(line.attribute)}\""
            if (line.style == LabelFontStyle.BOLD) commands += "SETBOLD 0"
        }
        commands += "PRINT 1,1"
        return commands.joinToString("\r\n")
    }

    fun printBytes(template: LabelTemplate, visitor: Visitor, encoding: PrinterEncoding): ByteArray =
        buildCommands(template, encoding) { attribute -> attributeValue(attribute, visitor) }
            .joinToString("\r\n", postfix = "\r\n")
            .toByteArray(charset(encoding.charsetName))

    private fun buildCommands(
        template: LabelTemplate,
        encoding: PrinterEncoding,
        value: (LabelAttribute) -> String
    ): List<String> {
        val dotsPerMm = template.resolution.dpi / 25.4f
        val commands = mutableListOf(
            "REM TARGET_DPI ${template.resolution.dpi}",
            "SIZE ${template.widthMm} mm,${template.heightMm} mm",
            "GAP 2 mm,0 mm",
            "DIRECTION 1",
            "CODEPAGE ${encoding.codePage}",
            "CLS"
        )
        layoutValues(template, value).forEach { placed ->
            val x = (placed.xMm * dotsPerMm).roundToInt()
            val y = (placed.yMm * dotsPerMm).roundToInt()
            val multiplier = (placed.fontSize / 16f).roundToInt().coerceIn(1, 10)
            if (placed.style == LabelFontStyle.BOLD) commands += "SETBOLD 1"
            if (placed.style == LabelFontStyle.ITALIC) commands += "REM STYLE ITALIC"
            if (placed.style == LabelFontStyle.UNDERLINE) commands += "REM STYLE UNDERLINE"
            commands += "TEXT $x,$y,\"${placed.fontName.tsplFontSafe()}\",0,$multiplier,$multiplier,\"${placed.text.tsplSafe()}\""
            if (placed.style == LabelFontStyle.UNDERLINE) {
                val underlineY = ((placed.yMm + placed.lineHeightMm) * dotsPerMm).roundToInt()
                val underlineWidth = (placed.estimatedWidthMm * dotsPerMm).roundToInt().coerceAtLeast(1)
                commands += "BAR $x,$underlineY,$underlineWidth,2"
            }
            if (placed.style == LabelFontStyle.BOLD) commands += "SETBOLD 0"
        }
        commands += "PRINT 1,1"
        return commands
    }

    private fun layoutValues(template: LabelTemplate, value: (LabelAttribute) -> String): List<PlacedLabelText> {
        val visible = template.lines.filter(LabelLineConfig::visible)
        if (visible.isEmpty()) return emptyList()
        val paddingMm = 3f
        val availableHeight = (template.heightMm - paddingMm * 2).coerceAtLeast(1f)
        var scale = 1f
        var groups = buildGroups(template, visible, scale, value)
        repeat(30) {
            val automaticHeight = groups.filter { it.line.automaticPosition }.sumOf { it.heightMm.toDouble() }.toFloat()
            if (automaticHeight <= availableHeight) return@repeat
            scale *= 0.94f
            groups = buildGroups(template, visible, scale, value)
        }

        val automatic = groups.filter { it.line.automaticPosition }
        val totalHeight = automatic.sumOf { it.heightMm.toDouble() }.toFloat()
        val gap = if (automatic.size > 1) ((availableHeight - totalHeight) / (automatic.size - 1)).coerceAtLeast(1f) else 0f
        var automaticY = paddingMm
        val result = mutableListOf<PlacedLabelText>()
        groups.forEach { group ->
            val startY = if (group.line.automaticPosition) automaticY else group.line.yMm.coerceAtLeast(0f)
            group.rows.forEachIndexed { index, row ->
                val width = estimateWidthMm(row, group.effectiveFontSize, group.line.style)
                val x = if (!group.line.automaticPosition) {
                    group.line.xMm.coerceAtLeast(0f)
                } else when (group.line.alignment) {
                    LabelTextAlignment.LEFT -> paddingMm
                    LabelTextAlignment.CENTER -> ((template.widthMm - width) / 2f).coerceAtLeast(paddingMm)
                    LabelTextAlignment.RIGHT -> (template.widthMm - paddingMm - width).coerceAtLeast(paddingMm)
                }
                result += PlacedLabelText(
                    lineId = group.line.id,
                    text = row,
                    xMm = x,
                    yMm = startY + index * group.lineHeightMm,
                    fontName = group.line.fontName.ifBlank { "3" },
                    fontSize = group.effectiveFontSize,
                    style = group.line.style,
                    alignment = group.line.alignment,
                    estimatedWidthMm = width,
                    lineHeightMm = group.lineHeightMm
                )
            }
            if (group.line.automaticPosition) automaticY += group.heightMm + gap
        }
        return result
    }

    private fun buildGroups(
        template: LabelTemplate,
        lines: List<LabelLineConfig>,
        scale: Float,
        value: (LabelAttribute) -> String
    ): List<LineGroup> = lines.map { line ->
        val effectiveSize = (line.fontSize * scale).roundToInt().coerceIn(8, 72)
        val startX = if (line.automaticPosition) 3f else line.xMm.coerceAtLeast(0f)
        val availableWidth = (template.widthMm - startX - 3f).coerceAtLeast(5f)
        val rows = wrap(value(line.attribute), availableWidth, effectiveSize, line.style)
        val lineHeight = effectiveSize * POINT_TO_MM * 1.18f
        LineGroup(line, rows, effectiveSize, lineHeight, rows.size * lineHeight)
    }

    private fun wrap(text: String, availableWidthMm: Float, fontSize: Int, style: LabelFontStyle): List<String> {
        if (text.isBlank()) return emptyList()
        val charWidth = (fontSize * POINT_TO_MM * 0.56f * if (style == LabelFontStyle.BOLD) 1.08f else 1f).coerceAtLeast(0.5f)
        val maxChars = floor(availableWidthMm / charWidth).toInt().coerceAtLeast(1)
        val rows = mutableListOf<String>()
        var current = ""
        text.trim().split(Regex("\\s+")).forEach { word ->
            val chunks = if (word.length > maxChars) word.chunked(maxChars) else listOf(word)
            chunks.forEach { chunk ->
                val candidate = if (current.isBlank()) chunk else "$current $chunk"
                if (candidate.length <= maxChars) current = candidate else {
                    if (current.isNotBlank()) rows += current
                    current = chunk
                }
            }
        }
        if (current.isNotBlank()) rows += current
        return rows.ifEmpty { listOf(text.take(maxChars)) }
    }

    private fun estimateWidthMm(text: String, fontSize: Int, style: LabelFontStyle): Float =
        text.length * fontSize * POINT_TO_MM * 0.56f * if (style == LabelFontStyle.BOLD) 1.08f else 1f

    private fun attributeValue(attribute: LabelAttribute, visitor: Visitor): String = when (attribute) {
        LabelAttribute.FULL_NAME -> visitor.fullName
        LabelAttribute.POSITION -> visitor.position
        LabelAttribute.COMPANY -> visitor.company
        LabelAttribute.CITY -> visitor.city
        LabelAttribute.VISITOR_TYPE -> visitor.type.title
        LabelAttribute.REGISTRATION_DATE -> visitor.checkedInAt?.let {
            DATE_FORMAT.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        }.orEmpty()
    }

    private fun placeholder(attribute: LabelAttribute): String = when (attribute) {
        LabelAttribute.FULL_NAME -> "{{ФАМИЛИЯ_ИМЯ}}"
        LabelAttribute.POSITION -> "{{ДОЛЖНОСТЬ}}"
        LabelAttribute.COMPANY -> "{{КОМПАНИЯ}}"
        LabelAttribute.CITY -> "{{ГОРОД}}"
        LabelAttribute.VISITOR_TYPE -> "{{ТИП_ПОСЕТИТЕЛЯ}}"
        LabelAttribute.REGISTRATION_DATE -> "{{ДАТА_РЕГИСТРАЦИИ}}"
    }

    private fun String.tsplSafe(): String = replace("\\", " ").replace("\"", "'")
    private fun String.tsplFontSafe(): String = replace("\\", "").replace("\"", "").ifBlank { "3" }

    private data class LineGroup(
        val line: LabelLineConfig,
        val rows: List<String>,
        val effectiveFontSize: Int,
        val lineHeightMm: Float,
        val heightMm: Float
    )

    private companion object {
        const val POINT_TO_MM = 25.4f / 72f
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }
}
