package org.library.excel.reader

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream

object ExcelWorkbookReader {

    private const val HEADER_ROW_INDEX = 0
    private val SUPPORTED_EXTENSIONS = setOf("xlsx", "xls")

    fun isSupportedFileType(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS

    fun open(bytes: ByteArray): Workbook = WorkbookFactory.create(ByteArrayInputStream(bytes))

    fun sheetNames(workbook: Workbook): List<String> = (0 until workbook.numberOfSheets).map { workbook.getSheetName(it) }

    fun headerRow(sheet: Sheet): List<String?> {
        val row = sheet.getRow(HEADER_ROW_INDEX) ?: return emptyList()
        val lastCellIndex = row.lastCellNum.toInt()
        if (lastCellIndex < 0) return emptyList()
        return (0 until lastCellIndex).map { cellToString(row.getCell(it)) }
    }

    fun dataRows(sheet: Sheet): Sequence<Pair<Int, List<String?>>> = sequence {
        var rowNumber = 0
        for (rowIndex in (HEADER_ROW_INDEX + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex)
            rowNumber++
            if (row == null || isBlankRow(row)) continue
            val lastCellIndex = row.lastCellNum.toInt()
            val values = if (lastCellIndex < 0) emptyList() else (0 until lastCellIndex).map { cellToString(row.getCell(it)) }
            yield(rowNumber to values)
        }
    }

    fun dataRowCount(sheet: Sheet): Int = dataRows(sheet).count()

    fun cellToString(cell: Cell?): String? {
        if (cell == null) return null
        val value = when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                val numeric = cell.numericCellValue
                if (numeric == Math.floor(numeric) && !numeric.isInfinite()) {
                    numeric.toLong().toString()
                } else {
                    numeric.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> cell.toString()
            CellType.BLANK, CellType._NONE, null -> null
            else -> cell.toString()
        }
        return value?.trim()?.takeIf { it.isNotBlank() }
    }

    fun columnLetter(zeroBasedIndex: Int): String {
        var index = zeroBasedIndex
        val builder = StringBuilder()
        while (index >= 0) {
            builder.insert(0, ('A' + (index % 26)))
            index = index / 26 - 1
        }
        return builder.toString()
    }

    private fun isBlankRow(row: Row): Boolean {
        val lastCellIndex = row.lastCellNum.toInt()
        if (lastCellIndex < 0) return true
        return (0 until lastCellIndex).all { cellToString(row.getCell(it)) == null }
    }
}
