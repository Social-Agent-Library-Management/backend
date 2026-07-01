# 프로젝트 개요

Spring Boot 3.5 + Kotlin 1.9 + JPA + QueryDSL + MySQL 8 기반 도서관 대출 관리 시스템.
패키지 루트: `io.github.fnzl54.library`

# 패키지 구조

```
core/
  application/     BaseRequest, BaseResponse, BaseService
  domain/
    entity/        JPA 엔티티 (BaseEntity 상속)
    repository/    JPA Repository + QueryDSL Repository
    share/         BaseEntity
  exception/       DomainException, SystemExceptionHandler
    error/         BaseErrorCode, GlobalErrorCode
  presentation/    SuccessResponse, ErrorResponse, ExternalResponse
    pagination/    Pagination, PaginationRequest

{domain}/          book | bookitem | loan
  presentation/
    get/           Read*Controller
    post/          Create*Controller
    patch/         Update*Controller, Return*Controller
  service/         Create*Service, Read*Service, Update*Service, Return*Service
```

# 핵심 패턴

## 서비스
모든 Service는 `BaseService<Q : BaseRequest, R : BaseResponse<*>>`를 상속한다. 핵심 로직은 `doExecute(request)`에 구현하고, Request / Response / ErrorCode는 Service 내부 중첩 클래스로 선언한다.

## 에러 처리
에러 코드는 해당 Service 내부 `ErrorCode` enum으로 선언하고, `DomainException.from(errorCode)`로 변환한다. 전역 에러는 `GlobalErrorCode`를 사용한다.

```kotlin
enum class ErrorCode(
    override val message: String,
    override val httpStatus: HttpStatus,
) : BaseErrorCode<DomainException> {
    BOOK_NOT_FOUND("도서를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
    override fun toException() = DomainException.from(this)
}
```

## 엔티티
모든 엔티티는 `BaseEntity`를 상속한다 (`id`, `createdAt`, `updatedAt`, `deleted` 포함). 연관관계는 `FetchType.LAZY`를 사용하고, 도메인 행위는 엔티티 메서드에 구현한다. 삭제는 소프트 딜리트(`deleted = true`)이며 조회 시 `deleted.isFalse` 조건을 추가한다.

## QueryDSL
복잡한 조회는 `{Domain}QueryRepository`로 분리한다. DTO 매핑은 `Projections.constructor`, 페이지네이션은 `PageableExecutionUtils.getPage`를 사용한다.

## 컨트롤러
Controller는 HTTP ↔ Service 변환 역할만 한다. HTTP Request/Response DTO는 Controller 내부 클래스로 선언하고, Swagger 어노테이션(`@Operation`, `@ApiResponse`, `@ApiErrorCode`)을 작성한다.

## 페이지네이션
페이지 번호는 1-based, 최대 크기는 100이며 `PaginationRequest`가 자동으로 강제한다.

# 네이밍

| 대상 | 규칙 | 예시 |
|------|------|------|
| Service | `{Action}{Domain}Service` | `CreateLoanService` |
| Controller | `{Action}{Domain}Controller` | `ReturnLoanController` |
| QueryDSL Repository | `{Domain}QueryRepository` | `BookQueryRepository` |
