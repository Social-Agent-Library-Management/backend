package org.library.excel.validation

import java.text.Normalizer
import java.util.Locale

object IdentityKeyNormalizer {

    const val UNKNOWN = "미상"

    private val MIDDLE_DOT_VARIANTS = charArrayOf('ㆍ', '・', '•', '‧')
    private const val CANONICAL_MIDDLE_DOT = '·'
    private val WHITESPACE_REGEX = Regex("[\\s　]+")
    private const val KEY_DELIMITER = ""

    fun normalize(title: String?, publisher: String?, author: String?): String {
        val normalizedTitle = normalizeField(title)
        val normalizedPublisher = normalizeField(publisher)
        val normalizedAuthor = normalizeField(author)
        return normalizedTitle + KEY_DELIMITER + normalizedPublisher + KEY_DELIMITER + normalizedAuthor
    }

    private fun normalizeField(value: String?): String {
        val base = value?.trim()?.takeIf { it.isNotBlank() } ?: UNKNOWN
        var normalized = base.replace('（', '(').replace('）', ')')
        for (variant in MIDDLE_DOT_VARIANTS) {
            normalized = normalized.replace(variant, CANONICAL_MIDDLE_DOT)
        }
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC)
        normalized = normalized.replace(WHITESPACE_REGEX, " ").trim()
        return normalized.lowercase(Locale.ROOT)
    }
}
