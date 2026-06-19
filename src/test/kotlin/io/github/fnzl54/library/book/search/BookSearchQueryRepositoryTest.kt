package io.github.fnzl54.library.book.search

import io.github.fnzl54.library.core.domain.document.BookDocument
import io.github.fnzl54.library.core.domain.repository.BookSearchRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.elasticsearch.DataElasticsearchTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.nio.file.Path

/**
 * ES + Nori 검색이 기존 FULLTEXT가 못 풀던 6대 요구사항을 해결하는지 검증한다.
 * MySQL이 필요 없도록 @DataElasticsearchTest 슬라이스를 사용하고, Nori 플러그인이 포함된
 * 커스텀 이미지를 Testcontainers로 빌드해 띄운다.
 */
@Testcontainers
@DataElasticsearchTest
@Import(BookSearchQueryRepository::class)
class BookSearchQueryRepositoryTest
    @Autowired
    constructor(
        private val bookSearchQueryRepository: BookSearchQueryRepository,
        private val bookSearchRepository: BookSearchRepository,
        private val operations: ElasticsearchOperations,
    ) {
        @BeforeEach
        fun setUp() {
            val indexOps = operations.indexOps(BookDocument::class.java)
            if (!indexOps.exists()) {
                indexOps.createWithMapping()
            }
            bookSearchRepository.deleteAll()
            bookSearchRepository.saveAll(SAMPLE_BOOKS)
            indexOps.refresh()
        }

        @Test
        @DisplayName("1글자 검색: '토' → '토지' (edge-ngram)")
        fun singleChar() {
            assertThat(search("토")).contains("토지")
        }

        @Test
        @DisplayName("조사 붙은 검색: '토지를' → '토지' (Nori 조사 제거)")
        fun josa() {
            assertThat(search("토지를")).contains("토지")
        }

        @Test
        @DisplayName("어간/활용형: '먹다' → '나는 어제 먹었다' (Nori 어간 추출)")
        fun stem() {
            assertThat(search("먹다")).contains("나는 어제 먹었다")
        }

        @Test
        @DisplayName("한영 혼용: 'Kotlin', 'Spring' 모두 매칭")
        fun mixedKoreanEnglish() {
            assertThat(search("Kotlin")).contains("Kotlin in Action")
            assertThat(search("Spring")).contains("스프링 부트 Spring Boot")
        }

        @Test
        @DisplayName("오타 보정: '데미얀' → '데미안' (fuzziness)")
        fun typo() {
            assertThat(search("데미얀")).contains("데미안")
        }

        @Test
        @DisplayName("가중치 정렬: 제목 매칭이 저자 매칭보다 상위")
        fun ranking() {
            val titles = search("스프링")
            assertThat(titles).isNotEmpty()
            assertThat(titles.first()).contains("스프링") // 제목 매칭(title^3)이 최상위
            assertThat(titles).contains("자바 ORM 표준 JPA") // 저자(스프링연구소) 매칭도 검색됨
            assertThat(titles.indexOf("자바 ORM 표준 JPA")).isGreaterThan(0) // 저자 매칭은 하위
        }

        private fun search(keyword: String): List<String> =
            bookSearchQueryRepository
                .search(keyword, PageRequest.of(0, 20))
                .content
                .map { it.title }

        companion object {
            @Container
            @JvmStatic
            private val elasticsearch =
                ElasticsearchContainer(noriImage())
                    .withEnv("xpack.security.enabled", "false")

            private fun noriImage(): DockerImageName =
                DockerImageName
                    .parse(
                        ImageFromDockerfile("library-es-nori-test", false)
                            .withFileFromPath("Dockerfile", Path.of("docker/elasticsearch/Dockerfile"))
                            .get(),
                    ).asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch")

            @JvmStatic
            @DynamicPropertySource
            fun esProps(registry: DynamicPropertyRegistry) {
                registry.add("spring.elasticsearch.uris") { "http://${elasticsearch.httpHostAddress}" }
            }

            private val SAMPLE_BOOKS =
                listOf(
                    BookDocument(1L, "8901234567", "토지", "박경리", "마로니에북스"),
                    BookDocument(2L, "8902345678", "데미안", "헤르만 헤세", "민음사"),
                    BookDocument(3L, "8903456789", "Kotlin in Action", "드미트리 제메로프", "에이콘"),
                    BookDocument(4L, "8904567890", "스프링 부트 Spring Boot", "김영한", "한빛미디어"),
                    BookDocument(5L, "8905678901", "스프링", "홍길동", "한빛미디어"),
                    BookDocument(6L, "8906789012", "자바 ORM 표준 JPA", "스프링연구소", "에이콘"),
                    BookDocument(7L, "8907890123", "나는 어제 먹었다", "작자미상", "테스트출판"),
                )
        }
    }
