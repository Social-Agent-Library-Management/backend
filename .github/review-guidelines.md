## 프로젝트 규칙 (코드 리뷰 체크 대상)

### 에러 처리
- 예외는 반드시 Service 내부 `ErrorCode` enum → `DomainException.from()` 흐름을 따를 것
- `throw RuntimeException(message)`, `throw ResponseStatusException(...)` 직접 사용 금지

### 엔티티
- 연관관계는 반드시 `FetchType.LAZY` 사용 (`FetchType.EAGER` 금지)
- 조회 쿼리에 반드시 `deleted.isFalse` 조건 포함 (소프트 딜리트 누락 주의)
- DB에서 직접 레코드 삭제 금지 (하드 딜리트 금지)

### QueryDSL
- 동적 쿼리에서 사용자 입력값을 문자열로 직접 삽입 금지 (SQL 인젝션)
- 반드시 `Expressions.numberTemplate` 등 QueryDSL 파라미터 바인딩 사용

### 서비스 / 컨트롤러
- 컨트롤러에서 Repository 직접 호출 금지
- `@Transactional` 없이 엔티티 상태 변경 금지
- 비즈니스 로직을 컨트롤러에 넣지 않을 것

### Kotlin
- Java 스타일 `Optional` 사용 금지, Kotlin `?` / `?:` 사용
- 다중 분기는 `if-else` 대신 `when` 권장

---

## 리뷰 지침

- 모든 리뷰 코멘트, PR 요약, 피드백은 반드시 한국어로 작성하세요.
- 이 프로젝트는 Kotlin + Spring Boot입니다. (Kotlin 문법이지만 Java 스타일로 작성될 가능성이 있습니다.)

## 리뷰 원칙

- 유지보수성·확장성·안정성 관점에서 왜 문제인지 근거를 함께 설명하세요.
- 문제를 지적할 때는 가능한 한 수정 방향이나 코드 예시를 함께 제공하세요.
- 심각도와 관계없이 발견한 이슈는 **모두** 코멘트로 남기세요. LOW 수준의 스타일이나 취향 이슈도 포함합니다.
- 각 코멘트 첫 줄에 심각도를 표기하세요: `[BLOCKER]` / `[HIGH]` / `[MEDIUM]` / `[LOW]`

### 심각도 기준
- **BLOCKER**: 머지 전 반드시 수정 (보안 취약점, 크래시, 데이터 손실 가능성)
- **HIGH**: 수정 강권 (로직 오류, 잘못된 결과 반환)
- **MEDIUM**: 수정 권장 (유지보수성 저하, 안정성 우려, 트랜잭션 경계 문제)
- **LOW**: 선택적 개선 (네이밍, Kotlin 관용구, 스타일)

## 리뷰 체크포인트

### 1. 로직 정확성

- 비즈니스 요구사항에 맞는 흐름인지, 엣지 케이스가 누락되지 않았는지 확인
- Null 안전성: Kotlin의 Null Safety를 적절히 활용하는지, Java 스타일 Optional 남용이 없는지 확인

### 2. 네이밍 및 코드 컨벤션

- Java 스타일 코드가 있다면 Kotlin다운 코드(Data Class, Scope Function, 확장 함수, 컬렉션 API 등)로 개선 제안
  - 제안 시 Java 코드와 Kotlin 코드를 비교하여 장점을 설명

### 3. 안정성 및 보안

- 트랜잭션 경계: 하나의 트랜잭션에서 처리해야 할 작업이 분리되어 있지 않은지 확인
- SQL 인젝션: 동적 쿼리 작성 시 파라미터 바인딩 사용 여부 확인

### 4. 성능 및 확장성

- N+1 쿼리 문제가 발생할 수 있는 조회 패턴 확인
- 연관관계 로딩 전략(FetchType.LAZY)이 올바르게 적용되었는지 확인

### 5. 유지보수성

- 에러 처리가 프로젝트의 기존 패턴(BaseErrorCode → DomainException)을 따르는지 확인
- 중복 코드가 있다면 공통화 가능 여부 검토
