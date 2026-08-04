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
import ru.atol.visitorregistration.model.LabelRotation
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
            "REM CONTENT_ROTATION ${template.rotation.degrees}",
            "SIZE ${template.widthMm} mm,${template.heightMm} mm",
            "GAP 2 mm,0 mm",
            "DIRECTION 1",
            "CODEPAGE ${encoding.codePage}",
            "CLS"
        )
        template.lines.filter(LabelLineConfig::visible).forEach { line ->
            val placements = samplePlacements[line.id].orEmpty()
            val rows = if (line.attribute == LabelAttribute.FULL_NAME) {
                placements.take(2).mapIndexed { index, placed ->
                    placed to if (index == 0) "{{ФАМИЛИЯ}}" else "{{ИМЯ}}"
                }
            } else {
                placements.firstOrNull()?.let { listOf(it to placeholder(line.attribute)) }.orEmpty()
            }
            rows.forEach { (placed, content) ->
                val point = rotateTextOrigin(template, placed)
                val x = (point.xMm * dotsPerMm).roundToInt().coerceIn(0, maxDotX(template))
                val y = (point.yMm * dotsPerMm).roundToInt().coerceIn(0, maxDotY(template))
                val (fontWidth, fontHeight) = fontDimensions(placed.fontName, placed.fontSize)
                if (placed.style == LabelFontStyle.ITALIC) commands += "REM STYLE ITALIC"
                if (placed.style == LabelFontStyle.UNDERLINE) commands += "REM STYLE UNDERLINE"
                commands += textCommand(x, y, placed.fontName, template.rotation.degrees, fontWidth, fontHeight, content)
            }
        }
        commands += "PRINT 1,${template.copies}"
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
            "REM CONTENT_ROTATION ${template.rotation.degrees}",
            "SIZE ${template.widthMm} mm,${template.heightMm} mm",
            "GAP 2 mm,0 mm",
            "DIRECTION 1",
            "CODEPAGE ${encoding.codePage}",
            "CLS"
        )
        layoutValues(template, value).forEach { placed ->
            val point = rotateTextOrigin(template, placed)
            val x = (point.xMm * dotsPerMm).roundToInt().coerceIn(0, maxDotX(template))
            val y = (point.yMm * dotsPerMm).roundToInt().coerceIn(0, maxDotY(template))
            val (fontWidth, fontHeight) = fontDimensions(placed.fontName, placed.fontSize)
            if (placed.style == LabelFontStyle.ITALIC) commands += "REM STYLE ITALIC"
            if (placed.style == LabelFontStyle.UNDERLINE) commands += "REM STYLE UNDERLINE"
            commands += textCommand(x, y, placed.fontName, template.rotation.degrees, fontWidth, fontHeight, placed.text)
            if (placed.style == LabelFontStyle.UNDERLINE) {
                val start = rotatePoint(template, placed.xMm, placed.yMm + placed.lineHeightMm)
                val end = rotatePoint(template, placed.xMm + placed.estimatedWidthMm, placed.yMm + placed.lineHeightMm)
                val barX = (minOf(start.xMm, end.xMm) * dotsPerMm).roundToInt()
                val barY = (minOf(start.yMm, end.yMm) * dotsPerMm).roundToInt()
                val barWidth = (kotlin.math.abs(end.xMm - start.xMm) * dotsPerMm).roundToInt().coerceAtLeast(2)
                val barHeight = (kotlin.math.abs(end.yMm - start.yMm) * dotsPerMm).roundToInt().coerceAtLeast(2)
                commands += "BAR $barX,$barY,$barWidth,$barHeight"
            }
        }
        commands += "PRINT 1,${template.copies}"
        return commands
    }

    private fun layoutValues(template: LabelTemplate, value: (LabelAttribute) -> String): List<PlacedLabelText> {
        val visible = template.lines.filter(LabelLineConfig::visible)
        if (visible.isEmpty()) return emptyList()
        val paddingMm = LABEL_PADDING_MM
        val contentWidthMm = minOf(template.layoutWidthMm.toFloat(), MAX_CONTENT_WIDTH_MM)
        val contentHeightMm = minOf(template.layoutHeightMm.toFloat(), MAX_CONTENT_HEIGHT_MM)
        val availableHeight = (contentHeightMm - paddingMm * 2).coerceAtLeast(1f)
        val groups = buildGroups(contentWidthMm, visible, 1f, value)

        val automatic = groups.filter { it.line.automaticPosition }
        val totalHeight = automatic.sumOf { it.heightMm.toDouble() }.toFloat()
        val gap = if (automatic.size > 1) {
            ((availableHeight - totalHeight) / (automatic.size - 1)).coerceAtLeast(0f)
        } else 0f
        var automaticY = paddingMm
        val result = mutableListOf<PlacedLabelText>()
        groups.forEach { group ->
            val maxStartY = (contentHeightMm - paddingMm - group.heightMm).coerceAtLeast(paddingMm)
            val startY = if (group.line.automaticPosition) automaticY else
                group.line.yMm.coerceIn(paddingMm, maxStartY)
            group.rows.forEachIndexed { index, row ->
                val rowY = startY + group.topOffsetMm + index * group.rowStepMm
                if (rowY + group.lineHeightMm > contentHeightMm + 0.01f) return@forEachIndexed
                val width = estimateWidthMm(row, group.effectiveFontSize, group.line.style)
                    .coerceAtMost((contentWidthMm - paddingMm * 2).coerceAtLeast(1f))
                val x = if (!group.line.automaticPosition) {
                    val maxX = (contentWidthMm - paddingMm - width).coerceAtLeast(paddingMm)
                    group.line.xMm.coerceIn(paddingMm, maxX)
                } else when (template.alignment) {
                    LabelTextAlignment.LEFT -> paddingMm
                    LabelTextAlignment.CENTER -> ((contentWidthMm - width) / 2f).coerceAtLeast(paddingMm)
                    LabelTextAlignment.RIGHT -> (contentWidthMm - paddingMm - width).coerceAtLeast(paddingMm)
                }
                result += PlacedLabelText(
                    lineId = group.line.id,
                    text = row,
                    xMm = x,
                    yMm = rowY,
                    fontName = group.line.fontName.ifBlank { "3" },
                    fontSize = group.effectiveFontSize,
                    style = group.line.style,
                    alignment = template.alignment,
                    estimatedWidthMm = width,
                    lineHeightMm = group.lineHeightMm
                )
            }
            if (group.line.automaticPosition) automaticY += group.heightMm + gap
        }
        return result
    }

    private fun buildGroups(
        contentWidthMm: Float,
        lines: List<LabelLineConfig>,
        scale: Float,
        value: (LabelAttribute) -> String
    ): List<LineGroup> = lines.map { line ->
        val effectiveSize = (line.fontSize * scale).roundToInt().coerceIn(8, 72)
        val startX = if (line.automaticPosition) LABEL_PADDING_MM else line.xMm.coerceAtLeast(0f)
        val availableWidth = (contentWidthMm - startX - LABEL_PADDING_MM).coerceAtLeast(5f)
        val rows = wrap(value(line.attribute), availableWidth, effectiveSize, line.style)
        val lineHeight = effectiveSize * POINT_TO_MM * 1.32f
        val rowStep = if (line.attribute == LabelAttribute.FULL_NAME) {
            (lineHeight - NAME_ROW_SPACING_REDUCTION_MM).coerceAtLeast(1f)
        } else {
            lineHeight
        }
        val topOffset = if (line.automaticPosition && line.attribute == LabelAttribute.FULL_NAME) NAME_TOP_OFFSET_MM else 0f
        val rowsHeight = if (rows.isEmpty()) 0f else lineHeight + (rows.size - 1) * rowStep
        LineGroup(line, rows, effectiveSize, lineHeight, rowStep, topOffset, topOffset + rowsHeight)
    }

    private fun wrap(text: String, availableWidthMm: Float, fontSize: Int, style: LabelFontStyle): List<String> {
        if (text.isBlank()) return emptyList()
        val charWidth = (fontSize * POINT_TO_MM * 0.56f * if (style == LabelFontStyle.BOLD) 1.08f else 1f).coerceAtLeast(0.5f)
        val maxChars = floor(availableWidthMm / charWidth).toInt().coerceAtLeast(1)
        return text.trim().split(Regex("\\r?\\n"), limit = 0).flatMap { paragraph ->
            wrapParagraph(paragraph, maxChars)
        }.ifEmpty { listOf("") }
    }

    private fun wrapParagraph(text: String, maxChars: Int): List<String> {
        if (text.isBlank()) return listOf("")
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

    private fun fontDimensions(fontName: String, fontSize: Int): Pair<Int, Int> {
        val scalable = fontName == "0" || fontName.endsWith(".TTF", ignoreCase = true)
        return if (scalable) {
            fontSize.coerceIn(1, 999) to fontSize.coerceIn(1, 999)
        } else {
            val multiplier = (fontSize / 16f).roundToInt().coerceIn(1, 10)
            multiplier to multiplier
        }
    }

    private fun rotatePoint(template: LabelTemplate, xMm: Float, yMm: Float): LabelPoint = when (template.rotation) {
        LabelRotation.DEG_0 -> LabelPoint(xMm, yMm)
        LabelRotation.DEG_90 -> LabelPoint(template.widthMm - yMm, xMm)
        LabelRotation.DEG_180 -> LabelPoint(template.widthMm - xMm, template.heightMm - yMm)
        LabelRotation.DEG_270 -> LabelPoint(yMm, template.heightMm - xMm)
    }

    private fun rotateTextOrigin(template: LabelTemplate, placed: PlacedLabelText): LabelPoint =
        rotatePoint(template, placed.xMm, placed.yMm)

    private fun maxDotX(template: LabelTemplate): Int =
        (template.widthMm * template.resolution.dpi / 25.4f).roundToInt().minus(1).coerceAtLeast(0)

    private fun maxDotY(template: LabelTemplate): Int =
        (template.heightMm * template.resolution.dpi / 25.4f).roundToInt().minus(1).coerceAtLeast(0)

    private fun attributeValue(attribute: LabelAttribute, visitor: Visitor): String = when (attribute) {
        LabelAttribute.FULL_NAME -> listOf(visitor.lastName, visitor.firstName).joinToString("\n")
        LabelAttribute.POSITION -> visitor.position
        LabelAttribute.COMPANY -> visitor.company
        LabelAttribute.CITY -> visitor.city
        LabelAttribute.VISITOR_TYPE -> visitor.type.title
        LabelAttribute.REGISTRATION_DATE -> visitor.checkedInAt?.let {
            DATE_FORMAT.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        }.orEmpty()
    }

    private fun placeholder(attribute: LabelAttribute): String = when (attribute) {
        LabelAttribute.FULL_NAME -> "{{ФАМИЛИЯ}}\n{{ИМЯ}}"
        LabelAttribute.POSITION -> "{{ДОЛЖНОСТЬ}}"
        LabelAttribute.COMPANY -> "{{КОМПАНИЯ}}"
        LabelAttribute.CITY -> "{{ГОРОД}}"
        LabelAttribute.VISITOR_TYPE -> "{{ТИП_ПОСЕТИТЕЛЯ}}"
        LabelAttribute.REGISTRATION_DATE -> "{{ДАТА_РЕГИСТРАЦИИ}}"
    }

    private fun String.tsplSafe(): String = replace("\\", " ").replace("\"", "'")
    private fun String.tsplFontSafe(): String = replace("\\", "").replace("\"", "").ifBlank { "3" }

    private fun textCommand(
        x: Int,
        y: Int,
        fontName: String,
        rotation: Int,
        fontWidth: Int,
        fontHeight: Int,
        content: String
    ): String = "TEXT $x,$y,\"${fontName.tsplFontSafe()}\",$rotation,$fontWidth,$fontHeight,\"${content.tsplSafe()}\""

    private data class LineGroup(
        val line: LabelLineConfig,
        val rows: List<String>,
        val effectiveFontSize: Int,
        val lineHeightMm: Float,
        val rowStepMm: Float,
        val topOffsetMm: Float,
        val heightMm: Float
    )

    private data class LabelPoint(val xMm: Float, val yMm: Float)

    private companion object {
        const val POINT_TO_MM = 25.4f / 72f
        const val LABEL_PADDING_MM = 0f
        const val NAME_TOP_OFFSET_MM = 3f
        const val NAME_ROW_SPACING_REDUCTION_MM = 2f
        const val MAX_CONTENT_WIDTH_MM = 66f
        const val MAX_CONTENT_HEIGHT_MM = 48f
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }
}
