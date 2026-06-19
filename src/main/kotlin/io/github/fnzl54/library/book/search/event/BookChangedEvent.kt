package io.github.fnzl54.library.book.search.event

/**
 * Book이 생성/수정/삭제되었음을 알리는 도메인 이벤트.
 * 커밋 후(AFTER_COMMIT) 리스너가 bookId 기준으로 ES 색인을 정합화한다.
 */
data class BookChangedEvent(
    val bookId: Long,
)
