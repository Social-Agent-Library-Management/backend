package org.library.bookitem.domain

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BookItemRepository : JpaRepository<BookItem, Long> {

    fun findByManagementNumber(managementNumber: String): BookItem?

    fun findAllByBookIdOrderByCreatedAtAsc(bookId: Long): List<BookItem>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bi from BookItem bi where bi.managementNumber = :managementNumber")
    fun findByManagementNumberForUpdate(@Param("managementNumber") managementNumber: String): BookItem?

    @Query(
        """
        select bi.bookId as bookId, count(bi) as itemCount
        from BookItem bi
        where bi.bookId in :bookIds
          and bi.status not in (
              org.library.bookitem.domain.BookItemStatus.DISPOSED,
              org.library.bookitem.domain.BookItemStatus.LOST
          )
        group by bi.bookId
        """,
    )
    fun countActiveByBookIdIn(@Param("bookIds") bookIds: List<Long>): List<BookItemCountProjection>
}

interface BookItemCountProjection {
    val bookId: Long
    val itemCount: Long
}
