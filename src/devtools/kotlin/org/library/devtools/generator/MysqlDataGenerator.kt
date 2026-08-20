package org.library.devtools.generator

import io.github.oshai.kotlinlogging.KotlinLogging
import org.library.bookitem.domain.BookItem
import org.library.devtools.db.MysqlConfig
import org.library.loan.domain.Loan
import java.sql.Date
import java.sql.Types
import java.time.LocalDate
import kotlin.random.Random

private val log = KotlinLogging.logger {}

private const val BOOK_COUNT = 100_000
private const val BOOK_ITEM_COUNT = 120_000
private const val LOAN_COUNT = 250_000

private const val CHUNK_SIZE = 1_000

private const val RANDOM_SEED = 20260818L

fun main() {
    log.warn {
        "이 도구는 ${MysqlConfig.jdbcUrl} 의 book/book_item/loan 테이블 전체를 비우고 " +
            "book=${BOOK_COUNT}, book_item=${BOOK_ITEM_COUNT}, " +
            "loan=${LOAN_COUNT}건을 재적재합니다."
    }

    val start = System.currentTimeMillis()

    FullTextIndexInstaller.dropIfExists()

    MysqlConfig.jdbcTemplate.execute("TRUNCATE TABLE loan")
    MysqlConfig.jdbcTemplate.execute("TRUNCATE TABLE book_item")
    MysqlConfig.jdbcTemplate.execute("TRUNCATE TABLE book")

    val titles = BookSeeder.seed()
    log.info { "book ${titles.size}건 적재 완료" }

    val bookItemResult = BookItemSeeder.seed(BOOK_COUNT)
    log.info { "book_item ${bookItemResult.managementNumbers.size}건 적재 완료" }

    LoanSeeder.seed(titles, bookItemResult)
    log.info { "loan ${LOAN_COUNT}건 적재 완료" }

    FullTextIndexInstaller.install()
    log.info { "FULLTEXT(ngram) 인덱스 재생성 완료" }

    val elapsedSeconds = (System.currentTimeMillis() - start) / 1000.0
    log.info { "전체 소요 시간: %.1f초".format(elapsedSeconds) }
}

// ── 워드뱅크 ──────────────────────────────────────────────────────────────
object WordBank {

    val tierS = listOf("자바", "스프링", "파이썬", "알고리즘", "데이터베이스")

    val tierA = listOf(
        "클린 코드", "클린 아키텍처", "리팩터링", "디자인 패턴", "운영체제",
        "네트워크", "자료구조", "인공지능", "머신러닝", "딥러닝",
        "클라우드", "도커", "쿠버네티스", "정보보안", "프론트엔드",
    )

    val tierB = listOf(
        "경제학원론", "심리학입문", "철학의 역사", "세계사", "한국사",
        "문학의 이해", "시집", "소설작법", "에세이 쓰기", "여행기",
        "건축의 이해", "미술사", "음악이론", "영화의 이해", "사진기술",
        "요리의 과학", "정원 가꾸기", "재테크 기초", "마케팅 전략", "회계원리",
        "법학개론", "정치학원론", "사회학입문", "인류학 산책", "생물학개론",
        "화학의 기초", "물리학 이야기", "천문학 입문", "지구과학", "환경과 생태",
    )

    val tierRare = listOf(
        "미니멀리즘 실천법", "발효 음식의 과학", "고대 그리스 철학 산책",
        "북유럽 신화 이야기", "손글씨 캘리그라피", "도시 양봉 입문",
        "빈티지 카메라 수리기", "제로웨이스트 살림법", "고서 복원의 세계",
        "전통 염색 기법", "수제 맥주 양조", "고산 등반 기록",
        "화훼 장식의 이해", "전통 목공예", "사찰음식 이야기",
        "옛날 지도로 읽는 역사", "야생화 관찰기", "고대 문자 해독",
        "전통 매듭공예", "천연 염료 이야기",
    )

    val subtitles: List<String?> = listOf(
        "입문", "기초부터 실전까지", "완벽 가이드", "실전 워크북", "핵심 정리",
        "개정판", "두 번째 이야기", "for 초보자", "깊이 파고들기", null,
    )

    val authorSurnames = listOf("김", "이", "박", "최", "정", "강", "조", "윤", "장", "임")
    val authorGivenNames = listOf(
        "민준", "서연", "지훈", "예은", "도윤", "수아", "시우", "하은", "주원", "지민",
        "현우", "다은", "준서", "예린", "우진", "서윤", "건우", "지우", "동현", "채원",
    )

    val publishers = listOf(
        "한빛미디어", "인사이트", "위키북스", "길벗", "제이펍",
        "에이콘출판", "이지스퍼블리싱", "생능출판", "책만", "동아시아",
        "민음사", "문학동네", "창비", "웅진지식하우스", "김영사",
    )

    val categoryCodes = listOf(
        "문학", "역사", "과학", "예술", "아동", "경제", "IT", "의학", "철학", "여행",
    )

    val departments = listOf(
        "컴퓨터공학과", "경영학과", "국어국문학과", "사학과", "물리학과",
        "화학과", "심리학과", "법학과", "행정학과", "디자인학과",
    )
}

// ── 희귀 키워드 배정 ───────────────────────────────────────────────────────
object RareKeywordPlan {

    private val rng = Random(RANDOM_SEED)

    private val assignments: Map<Int, String> = run {
        val words = WordBank.tierRare.flatMap { word -> List(rng.nextInt(3, 11)) { word } }
        (0 until BOOK_COUNT).shuffled(rng).take(words.size).zip(words).toMap()
    }

    fun forcedTitleFor(bookIndex: Int): String? = assignments[bookIndex]

    fun expectedCount(word: String): Int = assignments.values.count { it == word }
}

// ── book ──────────────────────────────────────────────────────────────────

private data class BookRow(val title: String, val author: String, val publisher: String)

private object BookSeeder {

    fun seed(): Array<String> {
        val rng = Random(RANDOM_SEED)
        val titles = Array(BOOK_COUNT) { i -> RareKeywordPlan.forcedTitleFor(i) ?: generateTitle(rng) }
        val rows = titles.map { title -> BookRow(title, generateAuthor(rng), generatePublisher(rng)) }

        MysqlConfig.jdbcTemplate.batchUpdate(
            "INSERT INTO book (created_at, updated_at, title, author, isbn, publisher) VALUES (NOW(6), NOW(6), ?, ?, NULL, ?)",
            rows,
            CHUNK_SIZE,
        ) { ps, row ->
            ps.setString(1, row.title)
            ps.setString(2, row.author)
            ps.setString(3, row.publisher)
        }

        return titles
    }

    private fun generateTitle(rng: Random): String {
        val roll = rng.nextDouble()
        val base = when {
            roll < 0.40 -> WordBank.tierS.random(rng)
            roll < 0.70 -> WordBank.tierA.random(rng)
            roll < 0.90 -> WordBank.tierB.random(rng)
            else -> (WordBank.tierS + WordBank.tierA + WordBank.tierB).random(rng)
        }
        val subtitle = WordBank.subtitles.random(rng)
        return if (subtitle != null) "$base $subtitle" else base
    }

    private fun generateAuthor(rng: Random): String =
        "${WordBank.authorSurnames.random(rng)}${WordBank.authorGivenNames.random(rng)}"

    private fun generatePublisher(rng: Random): String = WordBank.publishers.random(rng)
}

// ── book_item ─────────────────────────────────────────────────────────────

private data class BookItemSeedResult(val managementNumbers: Array<String>, val bookIds: IntArray)

private object BookItemSeeder {

    private val statusWeights = listOf(
        "AVAILABLE" to 0.70,
        "ON_LOAN" to 0.25,
        "LOST" to 0.03,
        "DISPOSED" to 0.02,
    )

    fun seed(bookCount: Int): BookItemSeedResult {
        val rng = Random(RANDOM_SEED + 1)
        val bookIds = IntArray(BOOK_ITEM_COUNT) { rng.nextInt(1, bookCount + 1) }
        val managementNumbers = Array(BOOK_ITEM_COUNT) { i ->
            val number = "${WordBank.categoryCodes.random(rng)}-${i + 1}"
            check(BookItem.MANAGEMENT_NUMBER_REGEX.matches(number)) { "잘못된 관리번호: $number" }
            number
        }
        val statuses = Array(BOOK_ITEM_COUNT) { pickStatus(rng) }

        MysqlConfig.jdbcTemplate.batchUpdate(
            "INSERT INTO book_item (created_at, updated_at, book_id, management_number, status) VALUES (NOW(6), NOW(6), ?, ?, ?)",
            (0 until BOOK_ITEM_COUNT).toList(),
            CHUNK_SIZE,
        ) { ps, i ->
            ps.setLong(1, bookIds[i].toLong())
            ps.setString(2, managementNumbers[i])
            ps.setString(3, statuses[i])
        }

        return BookItemSeedResult(managementNumbers, bookIds)
    }

    private fun pickStatus(rng: Random): String {
        val roll = rng.nextDouble()
        var acc = 0.0
        for ((status, weight) in statusWeights) {
            acc += weight
            if (roll < acc) return status
        }
        return statusWeights.last().first
    }
}

// ── loan ──────────────────────────────────────────────────────────────────

private data class LoanRow(
    val bookItemId: Int,
    val managementNumber: String,
    val bookTitle: String,
    val borrowerName: String,
    val department: String,
    val borrowerEmail: String?,
    val loanDate: LocalDate,
    val dueDate: LocalDate,
    val returnedAt: LocalDate?,
    val status: String,
)

private object LoanSeeder {

    fun seed(titles: Array<String>, bookItemResult: BookItemSeedResult) {
        val rng = Random(RANDOM_SEED + 2)
        val today = LocalDate.now()

        val rows = (0 until LOAN_COUNT).map {
            val bookItemId = rng.nextInt(1, BOOK_ITEM_COUNT + 1)
            val bookIdx = bookItemId - 1
            val bookId = bookItemResult.bookIds[bookIdx]
            val loanDate = today.minusDays(rng.nextLong(0, 730))
            val dueDate = loanDate.plusDays(Loan.LOAN_PERIOD_DAYS)
            val returned = rng.nextDouble() < 0.6
            val returnedAt = if (returned) loanDate.plusDays(rng.nextLong(1, 20)) else null

            LoanRow(
                bookItemId = bookItemId,
                managementNumber = bookItemResult.managementNumbers[bookIdx],
                bookTitle = titles[bookId - 1],
                borrowerName = "${WordBank.authorSurnames.random(rng)}${WordBank.authorGivenNames.random(rng)}",
                department = WordBank.departments.random(rng),
                borrowerEmail = if (rng.nextDouble() < 0.8) "user${rng.nextInt(1, 999_999)}@example.com" else null,
                loanDate = loanDate,
                dueDate = dueDate,
                returnedAt = returnedAt,
                status = if (returned) "RETURNED" else "ON_LOAN",
            )
        }

        MysqlConfig.jdbcTemplate.batchUpdate(
            """
            INSERT INTO loan (
                created_at, updated_at, book_item_id, management_number, book_title,
                borrower_name, department, borrower_email, loan_date, due_date, returned_at, status
            ) VALUES (NOW(6), NOW(6), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            rows,
            CHUNK_SIZE,
        ) { ps, row ->
            ps.setLong(1, row.bookItemId.toLong())
            ps.setString(2, row.managementNumber)
            ps.setString(3, row.bookTitle)
            ps.setString(4, row.borrowerName)
            ps.setString(5, row.department)
            if (row.borrowerEmail != null) ps.setString(6, row.borrowerEmail) else ps.setNull(6, Types.VARCHAR)
            ps.setDate(7, Date.valueOf(row.loanDate))
            ps.setDate(8, Date.valueOf(row.dueDate))
            if (row.returnedAt != null) ps.setDate(9, Date.valueOf(row.returnedAt)) else ps.setNull(9, Types.DATE)
            ps.setString(10, row.status)
        }
    }
}

// ── FULLTEXT(ngram) 인덱스 ───────────────────────────────────────────────
private object FullTextIndexInstaller {

    private const val INDEX_NAME = "idx_book_title_author_ft"

    fun dropIfExists() {
        val exists = MysqlConfig.jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'book' AND INDEX_NAME = ?
            """.trimIndent(),
            Int::class.java,
            INDEX_NAME,
        ) ?: 0
        if (exists > 0) {
            MysqlConfig.jdbcTemplate.execute("ALTER TABLE book DROP INDEX $INDEX_NAME")
        }
    }

    fun install() {
        MysqlConfig.jdbcTemplate.execute(
            "ALTER TABLE book ADD FULLTEXT INDEX $INDEX_NAME (title, author) WITH PARSER ngram",
        )
    }
}
