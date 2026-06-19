package io.github.fnzl54.library.core.domain.repository

import io.github.fnzl54.library.core.domain.document.BookDocument
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository

interface BookSearchRepository : ElasticsearchRepository<BookDocument, Long>
