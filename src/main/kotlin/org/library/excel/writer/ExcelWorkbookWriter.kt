package org.library.excel.writer

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ExcelWorkbookWriter {

    private const val MAX_SHEET_NAME_LENGTH = 31
    private const val ROW_ACCESS_WINDOW_SIZE = 200
    private val FORMULA_TRIGGER_CHARS = charArrayOf('=', '+', '-', '@', '\t', '\r')

    data class SheetData(val name: String, val headers: List<String>, val rows: List<List<Any?>>)

    fun build(sheets: List<SheetData>): ByteArray {
        val workbook = SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE)
        try {
            val headerStyle = workbook.createCellStyle().apply {
                val font = workbook.createFont().apply { bold = true }
                setFont(font)
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }

            for (sheetData in sheets) {
                val sheet = workbook.createSheet(sheetData.name.take(MAX_SHEET_NAME_LENGTH))

                val headerRow = sheet.createRow(0)
                sheetData.headers.forEachIndexed { index, header ->
                    val cell = headerRow.createCell(index)
                    cell.setCellValue(header)
                    cell.cellStyle = headerStyle
                }

                sheetData.rows.forEachIndexed { rowIndex, rowValues ->
                    val row = sheet.createRow(rowIndex + 1)
                    rowValues.forEachIndexed { columnIndex, value ->
                        writeCell(row.createCell(columnIndex), value)
                    }
                }
            }

            ByteArrayOutputStream().use { output ->
                workbook.write(output)
                return output.toByteArray()
            }
        } finally {
            workbook.dispose()
            workbook.close()
        }
    }

    private fun writeCell(cell: Cell, value: Any?) {
        when (value) {
            null -> {}
            is Boolean -> cell.setCellValue(value)
            is Int -> cell.setCellValue(value.toDouble())
            is Long -> cell.setCellValue(value.toDouble())
            is Double -> cell.setCellValue(value)
            is LocalDate -> cell.setCellValue(value.format(DateTimeFormatter.ISO_LOCAL_DATE))
            is LocalDateTime -> cell.setCellValue(value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            else -> cell.setCellValue(sanitize(value.toString()))
        }
    }

    private fun sanitize(text: String): String =
        if (text.isNotEmpty() && text[0] in FORMULA_TRIGGER_CHARS) "'$text" else text
}
