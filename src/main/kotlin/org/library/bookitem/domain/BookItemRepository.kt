package org.library.bookitem.domain

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BookItemRepository : JpaRepository<BookItem, Long> {

    fun findByManagementNumber(managementNumber: String): BookItem?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bi from BookItem bi where bi.managementNumber = :managementNumber")
    fun findByManagementNumberForUpdate(@Param("managementNumber") managementNumber: String): BookItem?
}
