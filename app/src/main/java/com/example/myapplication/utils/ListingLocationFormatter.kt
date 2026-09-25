package com.example.myapplication.utils

/** City label for listing cards — never region, and no "region / city" combined strings. */
object ListingLocationFormatter {
    fun cityOnly(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        return when {
            trimmed.contains("/") -> trimmed.substringAfterLast("/").trim()
            trimmed.contains("-") -> trimmed.substringAfterLast("-").trim()
            trimmed.contains(",") -> trimmed.substringAfterLast(",").trim()
            else -> trimmed
        }.ifEmpty { trimmed }
    }
}
