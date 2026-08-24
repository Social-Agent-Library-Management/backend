package org.library.external.opensearch.index

object BookIndex {

    const val NAME = "books"
    const val MAPPING_RESOURCE = "opensearch/books_index.json"
    const val SEARCH_PATH = "/$NAME/_search"
    const val REFRESH_PATH = "/$NAME/_refresh"
    const val INDEX_PATH = "/$NAME"

    fun documentPath(bookId: Long): String = "/$NAME/_doc/$bookId"
}
