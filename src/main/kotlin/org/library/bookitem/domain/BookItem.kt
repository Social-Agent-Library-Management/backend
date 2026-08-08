package org.library.bookitem.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.library.core.domain.BaseEntity

@Entity
@Table(name = "book_item")
class BookItem(
    bookId: Long,
    managementNumber: String,
) : BaseEntity() {

    @Column(nullable = false)
    var bookId: Long = bookId
        protected set

    @Column(nullable = false)
    var managementNumber: String = managementNumber
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BookItemStatus = BookItemStatus.AVAILABLE
        protected set

    init {
        require(MANAGEMENT_NUMBER_REGEX.matches(managementNumber)) {
            "관리번호는 (주제)-(번호) 형식이어야 합니다."
        }
    }

    fun changeStatus(status: BookItemStatus) {
        require(status == BookItemStatus.LOST || status == BookItemStatus.DISPOSED) {
            "분실 또는 폐기 상태로만 변경할 수 있습니다."
        }
        this.status = status
    }

    companion object {

        val MANAGEMENT_NUMBER_REGEX = Regex("^[^-\\s]+-\\d+$")
    }
}
