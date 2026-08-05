package ru.atol.visitorregistration

import org.junit.Assert.assertEquals
import org.junit.Test

class TextInputTest {
    @Test
    fun capitalizesFirstLetterIncludingCyrillicInput() {
        assertEquals("Иванов", capitalizeFirstLetter("иванов"))
        assertEquals("  Нижний Новгород", capitalizeFirstLetter("  нижний Новгород"))
        assertEquals("", capitalizeFirstLetter(""))
    }
}
