package org.library.excel.validation

import org.library.bookitem.domain.BookItem

object ManagementNumberNormalizer {

    private val TRAILING_DIGITS_REGEX = Regex("^(.*?)(\\d+)$")
    private val WHITESPACE_REGEX = Regex("\\s+")

    sealed interface NormalizationResult {
        data class AlreadyValid(val value: String) : NormalizationResult
        data class Normalized(val from: String, val to: String) : NormalizationResult
        data object Unfixable : NormalizationResult
    }

    fun normalize(raw: String): NormalizationResult {
        val compact = raw.replace(WHITESPACE_REGEX, "")
        if (BookItem.MANAGEMENT_NUMBER_REGEX.matches(compact)) {
            return NormalizationResult.AlreadyValid(compact)
        }

        val match = TRAILING_DIGITS_REGEX.find(compact) ?: return NormalizationResult.Unfixable
        val digits = match.groupValues[2]
        val prefix = match.groupValues[1].trim('-').replace("-", "")
        if (prefix.isBlank()) return NormalizationResult.Unfixable

        val candidate = "$prefix-$digits"
        return if (BookItem.MANAGEMENT_NUMBER_REGEX.matches(candidate)) {
            NormalizationResult.Normalized(compact, candidate)
        } else {
            NormalizationResult.Unfixable
        }
    }
}
