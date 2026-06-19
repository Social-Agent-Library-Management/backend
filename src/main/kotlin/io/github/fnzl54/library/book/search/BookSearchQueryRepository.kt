package io.github.fnzl54.library.book.search

import co.elastic.clients.elasticsearch._types.query_dsl.Operator
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import io.github.fnzl54.library.core.domain.document.BookDocument
import io.github.fnzl54.library.core.domain.repository.BookQueryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Repository

/**
 * Elasticsearch 기반 도서 검색.
 * - 본문(Nori) multi_match + fuzziness(AUTO): 형태소/어간/한영 + 오타 보정
 * - .ngram(edge-ngram) multi_match: 1글자/접두 매칭
 * - 필드 부스팅(title^3, author^2) + _score(BM25) 정렬: 가중치 정렬
 */
@Repository
class BookSearchQueryRepository(
    private val elasticsearchOperations: ElasticsearchOperations,
) {
    fun search(
        keyword: String?,
        pageable: Pageable,
    ): Page<BookQueryRepository.BookSummary> {
        val trimmed = keyword?.trim().orEmpty()

        val queryBuilder =
            NativeQuery
                .builder()
                .withQuery(buildQuery(trimmed))
                .withPageable(pageable)

        // 키워드가 없으면 _score가 무의미하므로 제목 오름차순으로 안정 정렬한다.
        if (trimmed.isBlank()) {
            queryBuilder.withSort(Sort.by(Sort.Order.asc("title.keyword")))
        }

        val searchHits = elasticsearchOperations.search(queryBuilder.build(), BookDocument::class.java)

        val summaries =
            searchHits.searchHits.map { hit ->
                val doc = hit.content
                BookQueryRepository.BookSummary(
                    bookId = doc.id,
                    isbn = doc.isbn,
                    title = doc.title,
                    author = doc.author,
                    publisher = doc.publisher,
                )
            }

        return PageImpl(summaries, pageable, searchHits.totalHits)
    }

    private fun buildQuery(keyword: String): Query =
        if (keyword.isBlank()) {
            Query.of { q -> q.matchAll { it } }
        } else {
            Query.of { q ->
                q.bool { b ->
                    b
                        // 본문(Nori): 모든 토큰이 매칭(AND)돼야 하는 정밀 절. fuzziness는 걸지 않는다.
                        // 형태소에 fuzzy를 걸면 흔한 단어로 과매칭(예: 24k 코퍼스에서 오타 1건이 211건)이 난다.
                        .should { s ->
                            s.multiMatch { m ->
                                m
                                    .query(keyword)
                                    .fields("title^3", "author^2", "publisher")
                                    .operator(Operator.And)
                            }
                        }
                        // edge-ngram: 1글자/접두 매칭 + 오타(fuzziness) 보정 담당.
                        // whitespace 검색 분석기로 단어를 통째 토큰화하므로 한글 오타도 여기서 잡되,
                        // prefix_length=2로 앞 2글자는 고정해 fuzzy 확장 범위를 좁힌다.
                        .should { s ->
                            s.multiMatch { m ->
                                m
                                    .query(keyword)
                                    .fields("title.ngram^2", "author.ngram")
                                    .operator(Operator.And)
                                    .fuzziness("AUTO")
                                    .prefixLength(2)
                            }
                        }.minimumShouldMatch("1")
                }
            }
        }
}
