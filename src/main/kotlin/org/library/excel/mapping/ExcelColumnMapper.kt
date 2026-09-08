package org.library.excel.mapping

import org.library.excel.reader.ExcelWorkbookReader

object ExcelColumnMapper {

    data class FieldDefinition(val header: String, val mappedTo: String, val required: Boolean)

    data class ColumnMapping(val column: String, val header: String, val mappedTo: String, val required: Boolean)

    data class MappedColumns(
        val managementNumberIndex: Int,
        val titleIndex: Int,
        val publisherIndex: Int?,
        val authorIndex: Int?,
        val columns: List<ColumnMapping>,
    )

    private val FIELD_DEFINITIONS = listOf(
        FieldDefinition("도서번호", "bookItem.managementNumber", true),
        FieldDefinition("도서명", "book.title", true),
        FieldDefinition("출판사명", "book.publisher", false),
        FieldDefinition("저자명", "book.author", false),
    )

    fun detect(headerRow: List<String?>): MappedColumns? {
        val indexByHeader = headerRow.withIndex()
            .filter { it.value != null }
            .associate { it.value!!.trim() to it.index }

        val columns = mutableListOf<ColumnMapping>()
        var managementNumberIndex: Int? = null
        var titleIndex: Int? = null
        var publisherIndex: Int? = null
        var authorIndex: Int? = null

        for (def in FIELD_DEFINITIONS) {
            val index = indexByHeader[def.header] ?: continue
            columns += ColumnMapping(ExcelWorkbookReader.columnLetter(index), def.header, def.mappedTo, def.required)
            when (def.mappedTo) {
                "bookItem.managementNumber" -> managementNumberIndex = index
                "book.title" -> titleIndex = index
                "book.publisher" -> publisherIndex = index
                "book.author" -> authorIndex = index
            }
        }

        val resolvedManagementNumberIndex = managementNumberIndex ?: return null
        val resolvedTitleIndex = titleIndex ?: return null

        return MappedColumns(resolvedManagementNumberIndex, resolvedTitleIndex, publisherIndex, authorIndex, columns)
    }
}
