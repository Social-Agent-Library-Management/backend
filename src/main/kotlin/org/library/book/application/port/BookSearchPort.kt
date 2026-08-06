package org.library.book.application.port

import org.library.core.presentation.PageRequestParams
import org.springframework.data.domain.Page

interface BookSearchPort {

    fun search(query: String?, page: PageRequestParams): Page<BookDocument>
}
