package io.github.fnzl54.library.core.domain.document

import io.github.fnzl54.library.core.domain.entity.Book
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.InnerField
import org.springframework.data.elasticsearch.annotations.MultiField
import org.springframework.data.elasticsearch.annotations.Setting

/**
 * Book 검색 전용 read model. MySQL Book(SSOT)의 검색 대상 필드만 색인한다.
 * 변동성이 큰 BookItem(소장본·대출 상태)은 색인하지 않고 MySQL에서 조회한다.
 *
 * - title/author: 본문은 Nori(형태소 분석 → 조사/어미/어간/한영 처리),
 *   .ngram 서브필드는 edge-ngram(min_gram=1)으로 1글자/접두 검색, .keyword는 정렬/완전일치.
 */
@Document(indexName = "book")
@Setting(settingPath = "elasticsearch/book-settings.json")
class BookDocument(
    @Id
    val id: Long,
    @Field(type = FieldType.Keyword)
    val isbn: String?,
    @MultiField(
        mainField = Field(type = FieldType.Text, analyzer = "korean"),
        otherFields = [
            InnerField(
                suffix = "ngram",
                type = FieldType.Text,
                analyzer = "edge_ngram_index",
                searchAnalyzer = "edge_ngram_search",
            ),
            InnerField(suffix = "keyword", type = FieldType.Keyword),
        ],
    )
    val title: String,
    @MultiField(
        mainField = Field(type = FieldType.Text, analyzer = "korean"),
        otherFields = [
            InnerField(
                suffix = "ngram",
                type = FieldType.Text,
                analyzer = "edge_ngram_index",
                searchAnalyzer = "edge_ngram_search",
            ),
            InnerField(suffix = "keyword", type = FieldType.Keyword),
        ],
    )
    val author: String,
    @Field(type = FieldType.Text, analyzer = "korean")
    val publisher: String?,
) {
    companion object {
        fun from(book: Book): BookDocument =
            BookDocument(
                id = requireNotNull(book.id) { "영속화되지 않은 Book은 색인할 수 없습니다." },
                isbn = book.isbn,
                title = book.title,
                author = book.author,
                publisher = book.publisher,
            )
    }
}
