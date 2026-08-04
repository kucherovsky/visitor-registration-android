package ru.atol.visitorregistration.model

import java.util.Locale

fun Visitor.matchesSearch(query: String): Boolean {
    val tokens = normalizeForSearch(query).split(Regex("\\s+")).filter(String::isNotBlank)
    if (tokens.isEmpty()) return false
    val searchable = normalizeForSearch(listOf(lastName, firstName, company).joinToString(" "))
    return tokens.all(searchable::contains)
}

private fun normalizeForSearch(value: String): String =
    value.lowercase(Locale.ROOT).replace('ё', 'е').trim()
