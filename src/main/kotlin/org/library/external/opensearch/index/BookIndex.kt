package org.library.external.opensearch.index

object BookIndex {

    const val NAME = "books"
    const val MAPPING_RESOURCE = "opensearch/books_index.json"
    const val SEARCH_PATH = "/$NAME/_search"
    const val REFRESH_PATH = "/$NAME/_refresh"
    const val INDEX_PATH = "/$NAME"

    const val TITLE_SUGGEST_NAME = "title_suggest"

    /**
     * `confidence` 를 푼 두 번째 suggester. 같은 요청에 나란히 실어 보내고, 엄격한 쪽이 빈손일 때만 쓴다.
     * 띄어쓴 제목을 붙여 입력하면서 오타까지 낸 경우가 여기서 살아난다(`"노치마과학"` → `놓지마 과학`).
     */
    const val TITLE_SUGGEST_LOOSE_NAME = "title_suggest_loose"

    /** 오타 교정 후보를 뽑는 사전. 자모로 풀어 색인한다. */
    const val TITLE_JAMO_FIELD = "title.jamo"

    /** 후보를 재채점하는 n-gram 언어모델. 같은 자모 표를 쓰되 shingle 이 얹힌다. */
    const val TITLE_JAMO_TRIGRAM_FIELD = "title.jamotri"
    const val TITLE_SUGGEST_GRAM_SIZE = 3

    const val TITLE_NGRAM_FIELD = "title.ngram"
    const val AUTHOR_NGRAM_FIELD = "author.ngram"

    fun documentPath(bookId: Long): String = "/$NAME/_doc/$bookId"
}
