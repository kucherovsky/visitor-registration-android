package ru.atol.visitorregistration.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitorSearchTest {
    private val visitor = Visitor(
        lastName = "Иванов",
        firstName = "Иван",
        company = "Альфа Технологии",
        type = VisitorType.PARTNER,
        source = VisitorSource.PARTNER_FILE
    )

    @Test
    fun matchesCaseInsensitivePartsFromSeveralWords() {
        assertTrue(visitor.matchesSearch("ИВАН"))
        assertTrue(visitor.matchesSearch("аль тех"))
        assertTrue(visitor.matchesSearch("иван альф"))
        assertFalse(visitor.matchesSearch("иван атол"))
    }
}
