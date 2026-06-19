package io.github.fnzl54.library.book.search

import io.github.fnzl54.library.core.domain.document.BookDocument
import io.github.fnzl54.library.core.domain.repository.BookRepository
import io.github.fnzl54.library.core.domain.repository.BookSearchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookIndexService(
    private val bookRepository: BookRepository,
    private val bookSearchRepository: BookSearchRepository,
) {
    /**
     * 현재 MySQL 상태를 기준으로 ES 색인을 정합화한다.
     * 존재하고 삭제되지 않았으면 색인(upsert), 없거나 소프트삭제 상태면 ES에서 제거한다.
     * (생성/수정/소프트삭제를 단일 경로로 안전하게 처리)
     */
    @Transactional(readOnly = true)
    fun reconcile(bookId: Long) {
        val book = bookRepository.findById(bookId).orElse(null)
        if (book == null || book.deleted) {
            bookSearchRepository.deleteById(bookId)
            return
        }
        bookSearchRepository.save(BookDocument.from(book))
    }
}
