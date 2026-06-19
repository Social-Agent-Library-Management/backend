package io.github.fnzl54.library.book.search

import io.github.fnzl54.library.core.domain.document.BookDocument
import io.github.fnzl54.library.core.domain.repository.BookRepository
import io.github.fnzl54.library.core.domain.repository.BookSearchRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookReindexService(
    private val bookRepository: BookRepository,
    private val bookSearchRepository: BookSearchRepository,
) {
    /** 삭제되지 않은 전체 Book을 ES로 벌크 색인한다. 초기 적재 및 정합성 보정에 사용. */
    @Transactional(readOnly = true)
    fun reindexAll(): Long {
        var pageNumber = 0
        var indexed = 0L
        while (true) {
            val page = bookRepository.findByDeletedFalse(PageRequest.of(pageNumber, BATCH_SIZE))
            val documents = page.content.map(BookDocument::from)
            if (documents.isNotEmpty()) {
                bookSearchRepository.saveAll(documents)
                indexed += documents.size
            }
            if (!page.hasNext()) break
            pageNumber++
        }
        return indexed
    }

    companion object {
        private const val BATCH_SIZE = 500
    }
}
