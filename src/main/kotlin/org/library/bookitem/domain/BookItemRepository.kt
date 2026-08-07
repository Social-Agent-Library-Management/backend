package org.library.bookitem.domain

import org.springframework.data.jpa.repository.JpaRepository

interface BookItemRepository : JpaRepository<BookItem, Long> {

}
