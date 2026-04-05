package io.github.fnzl54.library.loan.service

import io.github.fnzl54.library.core.domain.entity.Book
import io.github.fnzl54.library.core.domain.entity.BookItem
import io.github.fnzl54.library.core.domain.repository.BookItemRepository
import io.github.fnzl54.library.core.domain.repository.BookRepository
import io.github.fnzl54.library.core.domain.repository.LoanRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class CreateLoanServiceConcurrencyTest
    @Autowired
    constructor(
        private val createLoanService: CreateLoanService,
        private val bookRepository: BookRepository,
        private val bookItemRepository: BookItemRepository,
        private val loanRepository: LoanRepository,
    ) {
        private val callNumber = "CONCURRENCY-TEST-001"

        @AfterEach
        fun cleanup() {
            loanRepository.deleteAll()
            bookItemRepository.deleteAll()
            bookRepository.deleteAll()
        }

        @Test
        @DisplayName("동시에 같은 소장본을 대출 시도하면 1건만 성공해야 한다")
        fun `concurrent loan attempts should only succeed once`() {
            // given: 대출 가능한 소장본 1권 준비
            val book =
                bookRepository.save(
                    Book(
                        isbn = "0000000000",
                        title = "동시성 테스트 도서",
                        author = "동시성 테스트 작가",
                    ),
                )
            bookItemRepository.save(
                BookItem(
                    book = book,
                    callNumber = callNumber,
                    status = BookItem.Status.AVAILABLE,
                ),
            )

            val threadCount = 10
            val executor = Executors.newFixedThreadPool(threadCount)
            val successCount = AtomicInteger(0)
            val failCount = AtomicInteger(0)
            val latch = CountDownLatch(1)
            val doneLatch = CountDownLatch(threadCount)

            // when: 10개 스레드가 동시에 같은 소장본 대출 시도
            repeat(threadCount) { idx ->
                executor.submit {
                    try {
                        latch.await()
                        createLoanService.execute(
                            CreateLoanService.Request(
                                callNumber = callNumber,
                                name = "홍길동$idx",
                                department = "컴퓨터공학과",
                                email = "test$idx@example.com",
                                loanDate = LocalDate.now(),
                                dueDate = LocalDate.now().plusDays(14),
                            ),
                        )
                        successCount.incrementAndGet()
                    } catch (e: Exception) {
                        println("[Thread-$idx] FAILED: ${e::class.simpleName} - ${e.message}")
                        // e.cause?.let { println("  caused by: ${it::class.simpleName} - ${it.message}") }

                        failCount.incrementAndGet()
                    } finally {
                        doneLatch.countDown()
                    }
                }
            }
            latch.countDown()
            doneLatch.await(10, TimeUnit.SECONDS)
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)

            // then: 실제 DB 상태 확인
            val loans = loanRepository.findAll()
            val bookItem = bookItemRepository.findAll().find { it.callNumber == callNumber }

            println("==== 동시성 테스트 결과 ====")
            println("성공 요청: ${successCount.get()}")
            println("실패 요청: ${failCount.get()}")
            println("실제 생성된 대출 수: ${loans.size}")
            println("소장본 상태: ${bookItem?.status}")
            println("===========================")

            assertThat(loans).hasSize(1)
            assertThat(successCount.get()).isEqualTo(1)
            assertThat(failCount.get()).isEqualTo(threadCount - 1)
            assertThat(bookItem?.status).isEqualTo(BookItem.Status.BORROWED)
        }
    }
