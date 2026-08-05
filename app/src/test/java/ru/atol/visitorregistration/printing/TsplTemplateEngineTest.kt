package ru.atol.visitorregistration.printing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.atol.visitorregistration.model.LabelAttribute
import ru.atol.visitorregistration.model.LabelFontStyle
import ru.atol.visitorregistration.model.LabelTemplateKind
import ru.atol.visitorregistration.model.LabelRotation
import ru.atol.visitorregistration.model.LabelTextAlignment
import ru.atol.visitorregistration.model.PrinterEncoding
import ru.atol.visitorregistration.model.PrinterResolution
import ru.atol.visitorregistration.model.BOLD_LABEL_FONT
import ru.atol.visitorregistration.model.REGULAR_LABEL_FONT
import ru.atol.visitorregistration.model.defaultLabelTemplate

class TsplTemplateEngineTest {
    private val engine = TsplTemplateEngine()

    @Test
    fun defaultTemplatesUseAgreedAttributeOrder() {
        assertEquals(
            listOf(LabelAttribute.FULL_NAME, LabelAttribute.COMPANY, LabelAttribute.CITY),
            defaultLabelTemplate(LabelTemplateKind.VISITOR).lines.map { it.attribute }
        )
        assertEquals(
            listOf(LabelAttribute.FULL_NAME, LabelAttribute.POSITION, LabelAttribute.COMPANY),
            defaultLabelTemplate(LabelTemplateKind.EMPLOYEE).lines.map { it.attribute }
        )
    }

    @Test
    fun defaultTemplatesUseRequestedFontsSizesAndNormalStyle() {
        LabelTemplateKind.entries.forEach { kind ->
            val template = defaultLabelTemplate(kind)
            val lines = template.lines
            assertEquals(50, template.widthMm)
            assertEquals(70, template.heightMm)
            assertEquals(2, template.copies)
            assertEquals(LabelRotation.DEG_90, template.rotation)
            assertEquals(LabelTextAlignment.LEFT, template.alignment)
            assertTrue(lines.all { it.xMm == 0f && it.style == LabelFontStyle.NORMAL })
            assertEquals(22, lines.first { it.attribute == LabelAttribute.FULL_NAME }.fontSize)
            assertEquals(BOLD_LABEL_FONT, lines.first { it.attribute == LabelAttribute.FULL_NAME }.fontName)
        }
        val visitor = defaultLabelTemplate(LabelTemplateKind.VISITOR).lines
        assertEquals(REGULAR_LABEL_FONT, visitor.first { it.attribute == LabelAttribute.COMPANY }.fontName)
        assertEquals(12, visitor.first { it.attribute == LabelAttribute.COMPANY }.fontSize)
        assertEquals(12, visitor.first { it.attribute == LabelAttribute.CITY }.fontSize)
        assertEquals(REGULAR_LABEL_FONT, visitor.first { it.attribute == LabelAttribute.CITY }.fontName)
        val employee = defaultLabelTemplate(LabelTemplateKind.EMPLOYEE).lines
        assertEquals(BOLD_LABEL_FONT, employee.first { it.attribute == LabelAttribute.COMPANY }.fontName)
        assertEquals(16, employee.first { it.attribute == LabelAttribute.COMPANY }.fontSize)
        assertEquals(REGULAR_LABEL_FONT, employee.first { it.attribute == LabelAttribute.POSITION }.fontName)
        assertEquals(10, employee.first { it.attribute == LabelAttribute.POSITION }.fontSize)
    }

    @Test
    fun tsplCoordinatesChangeWithPrinterResolution() {
        val template203 = engine.applyAutomaticPositions(defaultLabelTemplate(LabelTemplateKind.VISITOR))
        val template300 = template203.copy(resolution = PrinterResolution.DPI_300)

        val text203 = engine.templateText(template203)
        val text300 = engine.templateText(template300)

        assertTrue(text203.contains("REM TARGET_DPI 203"))
        assertTrue(text300.contains("REM TARGET_DPI 300"))
        assertTrue(text203.contains("SIZE 50 mm,70 mm"))
        assertFalse(firstTextCommand(text203) == firstTextCommand(text300))
    }

    @Test
    fun longValuesWrapAndRemainInsideLabel() {
        val template = defaultLabelTemplate(LabelTemplateKind.VISITOR)
        val placements = engine.layout(template, engine.sampleVisitor(LabelTemplateKind.VISITOR))
        val companyLineId = template.lines.first { it.attribute == LabelAttribute.COMPANY }.id
        val companyRows = placements.filter { it.lineId == companyLineId }

        assertTrue(companyRows.size > 1)
        assertTrue(placements.all { it.yMm + it.lineHeightMm <= template.layoutHeightMm + 0.1f })
    }

    @Test
    fun employeeRowsDoNotOverlap() {
        val template = defaultLabelTemplate(LabelTemplateKind.EMPLOYEE)
        val placements = engine.layout(template, engine.sampleVisitor(LabelTemplateKind.EMPLOYEE))
            .sortedBy { it.yMm }
        val nameLineId = template.lines.first { it.attribute == LabelAttribute.FULL_NAME }.id

        placements.zipWithNext().forEach { (current, next) ->
            if (current.lineId != nameLineId || next.lineId != nameLineId) {
                assertTrue(current.yMm + current.lineHeightMm <= next.yMm + 0.01f)
            }
        }
        assertTrue(placements.all { it.yMm + it.lineHeightMm <= template.layoutHeightMm })
    }

    @Test
    fun templateAlignmentAppliesToEveryAutomaticRow() {
        val template = defaultLabelTemplate(LabelTemplateKind.EMPLOYEE).copy(
            alignment = LabelTextAlignment.CENTER
        )
        val placements = engine.layout(template, engine.sampleVisitor(LabelTemplateKind.EMPLOYEE))

        assertTrue(placements.all { it.alignment == LabelTextAlignment.CENTER })
        assertTrue(placements.all {
            kotlin.math.abs(it.xMm - (66f - it.estimatedWidthMm) / 2f) < 0.1f
        })
    }

    @Test
    fun manualCoordinatesAreKept() {
        val template = defaultLabelTemplate(LabelTemplateKind.EMPLOYEE).let { original ->
            original.copy(lines = original.lines.mapIndexed { index, line ->
                if (index == 0) line.copy(xMm = 15f, yMm = 20f, automaticPosition = false) else line
            })
        }
        val placed = engine.layout(template, engine.sampleVisitor(LabelTemplateKind.EMPLOYEE))
            .first { it.lineId == template.lines.first().id }

        assertEquals(15f, placed.xMm)
        assertEquals(20f, placed.yMm)
    }

    @Test
    fun templateContainsCopyablePlaceholders() {
        val text = engine.templateText(defaultLabelTemplate(LabelTemplateKind.EMPLOYEE))

        assertTrue(text.contains("{{ФАМИЛИЯ}}"))
        assertTrue(text.contains("{{ИМЯ}}"))
        assertTrue(text.contains("{{ДОЛЖНОСТЬ}}"))
        assertTrue(text.contains("{{КОМПАНИЯ}}"))
        assertFalse(text.contains("{{ФАМИЛИЯ\r\n"))
    }

    @Test
    fun employeeSamplePrintContainsFullName() {
        val template = defaultLabelTemplate(LabelTemplateKind.EMPLOYEE)
        val sample = engine.sampleVisitor(LabelTemplateKind.EMPLOYEE)
        val text = String(
            engine.printBytes(template, sample, PrinterEncoding.WINDOWS_1251),
            charset(PrinterEncoding.WINDOWS_1251.charsetName)
        )

        assertTrue(text.contains("Петрова"))
        assertTrue(text.contains("Мария"))
        assertTrue(text.contains("Руководитель"))
        assertTrue(text.contains("направления"))
        assertFalse(text.contains("SETBOLD"))
        assertEquals(1, text.lineSequence().count { it.contains("Петрова") })
        assertEquals(1, text.lineSequence().count { it.contains("Мария") })
    }

    @Test
    fun surnameAndFirstNameAlwaysUseSeparateRows() {
        LabelTemplateKind.entries.forEach { kind ->
            val template = defaultLabelTemplate(kind)
            val nameLineId = template.lines.first { it.attribute == LabelAttribute.FULL_NAME }.id
            val rows = engine.layout(template, engine.sampleVisitor(kind)).filter { it.lineId == nameLineId }

            assertEquals(2, rows.size)
            assertTrue(rows.all { it.fontSize == 22 })
            assertEquals(3f, rows.first().yMm)
            assertEquals(rows.first().lineHeightMm - 2f, rows[1].yMm - rows[0].yMm, 0.01f)
            assertFalse(rows.first().text.contains(' '))
        }
    }

    @Test
    fun selectedLongFieldsUseSixtySixMillimeterLimit() {
        val visitorTemplate = defaultLabelTemplate(LabelTemplateKind.VISITOR)
        val visitorCompanyId = visitorTemplate.lines.first { it.attribute == LabelAttribute.COMPANY }.id
        val visitorCompany = engine.layout(visitorTemplate, engine.sampleVisitor(LabelTemplateKind.VISITOR))
            .filter { it.lineId == visitorCompanyId }
        assertTrue(visitorCompany.all { it.estimatedWidthMm <= 66.01f })

        val employeeTemplate = defaultLabelTemplate(LabelTemplateKind.EMPLOYEE)
        val employeePositionId = employeeTemplate.lines.first { it.attribute == LabelAttribute.POSITION }.id
        val employeePosition = engine.layout(employeeTemplate, engine.sampleVisitor(LabelTemplateKind.EMPLOYEE))
            .filter { it.lineId == employeePositionId }
        assertTrue(employeePosition.all { it.estimatedWidthMm <= 66.01f })

        val wideEmployeeTemplate = employeeTemplate.copy(widthMm = 70, heightMm = 100)
        val wideEmployeePosition = engine.layout(
            wideEmployeeTemplate,
            engine.sampleVisitor(LabelTemplateKind.EMPLOYEE)
        ).filter { it.lineId == employeePositionId }
        assertTrue(wideEmployeePosition.all { it.estimatedWidthMm <= 66.01f })
    }

    @Test
    fun everyPrintedRowStaysInsideSixtySixMillimeterContentWidth() {
        LabelTemplateKind.entries.forEach { kind ->
            val template = defaultLabelTemplate(kind).copy(widthMm = 70, heightMm = 100)
            val placements = engine.layout(template, engine.sampleVisitor(kind))

            assertTrue(placements.isNotEmpty())
            assertTrue(placements.all { it.xMm + it.estimatedWidthMm <= 66.01f })
        }
    }

    @Test
    fun everyPrintedRowStaysInsideFortyEightMillimeterContentHeight() {
        LabelTemplateKind.entries.forEach { kind ->
            val template = defaultLabelTemplate(kind)
            val placements = engine.layout(template, engine.sampleVisitor(kind))

            assertTrue(placements.isNotEmpty())
            assertTrue(placements.all { it.yMm + it.lineHeightMm <= 48.01f })
        }
    }

    @Test
    fun requestedCopyCountIsWrittenToPrintCommand() {
        val template = defaultLabelTemplate(LabelTemplateKind.VISITOR).copy(copies = 4)

        assertTrue(engine.templateText(template).endsWith("PRINT 1,4"))
    }

    @Test
    fun rotatedTemplateKeepsPhysicalSizeAndRotatesContent() {
        val template = defaultLabelTemplate(LabelTemplateKind.EMPLOYEE).copy(
            widthMm = 50,
            heightMm = 70,
            rotation = LabelRotation.DEG_90
        )
        val text = engine.templateText(template)

        assertEquals(70, template.layoutWidthMm)
        assertEquals(50, template.layoutHeightMm)
        assertTrue(text.contains("SIZE 50 mm,70 mm"))
        assertTrue(text.contains("REM CONTENT_ROTATION 90"))
        assertTrue(firstTextCommand(text).contains("\",90,"))
    }

    @Test
    fun textOriginsStayInsidePhysicalLabelForEveryRotation() {
        LabelRotation.entries.forEach { rotation ->
            val template = defaultLabelTemplate(LabelTemplateKind.EMPLOYEE).copy(
                widthMm = 50,
                heightMm = 70,
                rotation = rotation
            )
            val text = String(
                engine.printBytes(template, engine.sampleVisitor(LabelTemplateKind.EMPLOYEE), PrinterEncoding.WINDOWS_1251),
                charset(PrinterEncoding.WINDOWS_1251.charsetName)
            )
            val maxX = template.widthMm * template.resolution.dpi / 25.4f
            val maxY = template.heightMm * template.resolution.dpi / 25.4f
            text.lineSequence().filter { it.startsWith("TEXT ") }.forEach { command ->
                val coordinates = command.removePrefix("TEXT ").substringBefore(',').toFloat() to
                    command.removePrefix("TEXT ").substringAfter(',').substringBefore(',').toFloat()
                assertTrue(coordinates.first in 0f..maxX)
                assertTrue(coordinates.second in 0f..maxY)
            }
        }
    }

    @Test
    fun trueTypeFontUsesPointSizeInsteadOfBitmapMultiplier() {
        val original = defaultLabelTemplate(LabelTemplateKind.EMPLOYEE)
        val template = original.copy(rotation = LabelRotation.DEG_0, lines = original.lines.mapIndexed { index, line ->
            line.copy(visible = index == 0, fontName = "ARIAL.TTF", fontSize = 28)
        })
        val text = engine.templateText(template)

        assertTrue(firstTextCommand(text).contains("\"ARIAL.TTF\",0,28,28"))
    }

    private fun firstTextCommand(value: String): String = value.lineSequence().first { it.startsWith("TEXT ") }
}
