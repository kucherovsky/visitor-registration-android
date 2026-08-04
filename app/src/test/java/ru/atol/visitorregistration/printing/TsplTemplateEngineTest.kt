package ru.atol.visitorregistration.printing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.atol.visitorregistration.model.LabelAttribute
import ru.atol.visitorregistration.model.LabelTemplateKind
import ru.atol.visitorregistration.model.PrinterResolution
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
    fun tsplCoordinatesChangeWithPrinterResolution() {
        val template203 = engine.applyAutomaticPositions(defaultLabelTemplate(LabelTemplateKind.VISITOR))
        val template300 = template203.copy(resolution = PrinterResolution.DPI_300)

        val text203 = engine.templateText(template203)
        val text300 = engine.templateText(template300)

        assertTrue(text203.contains("REM TARGET_DPI 203"))
        assertTrue(text300.contains("REM TARGET_DPI 300"))
        assertTrue(text203.contains("SIZE 70 mm,50 mm"))
        assertFalse(firstTextCommand(text203) == firstTextCommand(text300))
    }

    @Test
    fun longValuesWrapAndRemainInsideLabel() {
        val template = defaultLabelTemplate(LabelTemplateKind.VISITOR)
        val placements = engine.layout(template, engine.sampleVisitor(LabelTemplateKind.VISITOR))
        val companyLineId = template.lines.first { it.attribute == LabelAttribute.COMPANY }.id
        val companyRows = placements.filter { it.lineId == companyLineId }

        assertTrue(companyRows.size > 1)
        assertTrue(placements.all { it.yMm + it.lineHeightMm <= template.heightMm + 0.1f })
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

        assertTrue(text.contains("{{ФАМИЛИЯ_ИМЯ}}"))
        assertTrue(text.contains("{{ДОЛЖНОСТЬ}}"))
        assertTrue(text.contains("{{КОМПАНИЯ}}"))
        assertFalse(text.contains("{{ФАМИЛИЯ\r\n"))
    }

    private fun firstTextCommand(value: String): String = value.lineSequence().first { it.startsWith("TEXT ") }
}
