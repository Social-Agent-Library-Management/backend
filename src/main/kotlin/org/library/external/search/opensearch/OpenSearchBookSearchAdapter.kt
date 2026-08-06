package org.library.external.search.opensearch

import org.library.book.application.port.BookDocument
import org.library.book.application.port.BookSearchPort
import org.library.core.presentation.PageRequestParams
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["search.engine"], havingValue = "opensearch")
class OpenSearchBookSearchAdapter : BookSearchPort {

    override fun search(query: String?, page: PageRequestParams): Page<BookDocument> {
        throw NotImplementedError(
            "OpenSearch 검색 어댑터는 아직 스켈레톤입니다. search.engine=rdb 를 사용하세요.",
        )
    }
}
