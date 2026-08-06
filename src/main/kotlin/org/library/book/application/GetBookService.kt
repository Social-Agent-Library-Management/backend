package org.library.book.application

import org.library.book.domain.BookRepository
import org.library.book.domain.error.BookError
import org.library.book.dto.BookResponse
import org.library.core.application.Result
import org.library.core.application.err
import org.library.core.application.ok
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class GetBookService(
    private val bookRepository: BookRepository,
) {

    fun execute(id: Long): Result<BookResponse, BookError> {
        val book = bookRepository.findByIdAndDeletedAtIsNull(id)
            ?: return BookError.NOT_FOUND.err()
        return BookResponse.from(book).ok()
    }
}
