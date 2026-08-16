package org.library.book.application

import org.library.book.domain.BookRepository
import org.library.book.domain.error.BookError
import org.library.bookitem.domain.BookItemRepository
import org.library.core.application.Result
import org.library.core.application.err
import org.library.core.application.ok
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteBookService(
    private val bookRepository: BookRepository,
    private val bookItemRepository: BookItemRepository,
) {

    @Transactional
    fun execute(id: Long): Result<Unit, BookError> {
        val book = bookRepository.findByIdAndDeletedAtIsNull(id)
            ?: return BookError.NOT_FOUND.err()
        book.softDelete()
        bookItemRepository.findAllByBookIdOrderByCreatedAtAsc(id).forEach { it.softDelete() }
        return Unit.ok()
    }
}
