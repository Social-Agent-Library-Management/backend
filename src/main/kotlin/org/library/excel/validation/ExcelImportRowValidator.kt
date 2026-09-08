package org.library.excel.validation

import org.library.excel.domain.ExcelImportRowCode
import org.library.excel.domain.ExcelImportRowJudgement
import org.springframework.stereotype.Component

@Component
class ExcelImportRowValidator {

    data class RowInput(
        val managementNumber: String?,
        val title: String?,
        val publisher: String?,
        val author: String?,
    )

    data class Outcome(
        val normalizedManagementNumber: String?,
        val identityKey: String,
        val judgement: ExcelImportRowJudgement,
        val code: ExcelImportRowCode?,
        val message: String?,
        val errorField: String?,
    )

    fun validate(
        input: RowInput,
        managementNumbersSeenInFile: Map<String, Int>,
        identityKeysSeenInFile: Set<String>,
        managementNumberExistsInDb: (String) -> Boolean,
        existingBookIdByIdentityKey: (String) -> Long?,
    ): Outcome {
        val identityKey = IdentityKeyNormalizer.normalize(input.title, input.publisher, input.author)

        val managementNumberRaw = input.managementNumber?.trim()
        if (managementNumberRaw.isNullOrBlank()) {
            return errorOutcome(identityKey, ExcelImportRowCode.MANAGEMENT_NUMBER_REQUIRED, "도서번호가 비어 있습니다.", "managementNumber")
        }

        val title = input.title?.trim()
        if (title.isNullOrBlank()) {
            return errorOutcome(identityKey, ExcelImportRowCode.TITLE_REQUIRED, "도서명이 비어 있습니다.", "title")
        }

        val normalization = ManagementNumberNormalizer.normalize(managementNumberRaw)
        if (normalization !is ManagementNumberNormalizer.NormalizationResult.AlreadyValid) {
            val suggestion = (normalization as? ManagementNumberNormalizer.NormalizationResult.Normalized)?.to
            val message = if (suggestion != null) {
                "관리번호 형식이 올바르지 않습니다. (주제)-(번호) 형식으로 직접 수정해 주세요. 예: $suggestion"
            } else {
                "관리번호 형식이 올바르지 않습니다. (주제)-(번호) 형식으로 직접 수정해 주세요."
            }
            return errorOutcome(identityKey, ExcelImportRowCode.MANAGEMENT_NUMBER_INVALID_FORMAT, message, "managementNumber")
        }

        val resolvedManagementNumber = normalization.value

        val duplicateRowNumber = managementNumbersSeenInFile[resolvedManagementNumber]
        if (duplicateRowNumber != null) {
            return Outcome(
                resolvedManagementNumber,
                identityKey,
                ExcelImportRowJudgement.ERROR,
                ExcelImportRowCode.DUPLICATE_MANAGEMENT_NUMBER_IN_FILE,
                "도서번호 중복 (같은 파일 내 ${duplicateRowNumber}행)",
                "managementNumber",
            )
        }
        if (managementNumberExistsInDb(resolvedManagementNumber)) {
            return errorOutcome(identityKey, ExcelImportRowCode.DUPLICATE_MANAGEMENT_NUMBER_IN_DB, "이미 등록된 관리번호입니다.", "managementNumber", resolvedManagementNumber)
        }

        if (identityKey in identityKeysSeenInFile || existingBookIdByIdentityKey(identityKey) != null) {
            return Outcome(
                resolvedManagementNumber,
                identityKey,
                ExcelImportRowJudgement.WARN,
                ExcelImportRowCode.EXISTING_BOOK_ITEM_ADDED,
                "동일한 도서가 이미 존재합니다 · 소장본만 추가됩니다.",
                null,
            )
        }

        if (input.author.isNullOrBlank()) {
            return errorOutcome(identityKey, ExcelImportRowCode.AUTHOR_BLANK, "저자 정보가 비어 있습니다.", "author")
        }
        if (input.publisher.isNullOrBlank()) {
            return errorOutcome(identityKey, ExcelImportRowCode.PUBLISHER_BLANK, "출판사 정보가 비어 있습니다.", "publisher")
        }

        return Outcome(resolvedManagementNumber, identityKey, ExcelImportRowJudgement.OK, null, null, null)
    }

    private fun errorOutcome(
        identityKey: String,
        code: ExcelImportRowCode,
        message: String,
        errorField: String,
        normalizedManagementNumber: String? = null,
    ) = Outcome(normalizedManagementNumber, identityKey, ExcelImportRowJudgement.ERROR, code, message, errorField)
}
