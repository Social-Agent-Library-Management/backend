Provide a code review for the given pull request.

## Project Review Guidelines

- All review comments, PR summaries, and feedback must be written in **Korean**.
- This project is **Kotlin + Spring Boot** (Modular Use-Case Architecture, 선택적 헥사고날).
- Judge project conventions/rules against the root `CLAUDE.md` (강제 관례). 설계 배경·근거가
  필요하면 `docs/architecture.md`를 함께 참고하되, **위반 판정의 근거는 항상 `CLAUDE.md`**여야
  한다 (architecture.md는 설명용 문서이므로 단독 근거로 쓰지 않는다).

### Review principles

- Explain **why** something is a problem from a maintainability/scalability/stability
  standpoint.
- Whenever you flag a problem, provide a **fix direction or example code** wherever possible.
- Post **every** issue you find regardless of severity (including LOW-level style/preference
  issues).
- Prefix each comment's first line with a severity tag: `[BLOCKER]` / `[HIGH]` / `[MEDIUM]` /
  `[LOW]`.

### Comment format (readability)

Don't write one long paragraph. Use subheadings and blank lines in this structure instead:

```
[SEVERITY] One-line summary

**Problem**: what it is and why it matters (1-2 sentences)

**Evidence**: reproduction condition, referenced code/docs

**Fix direction**: concrete fix

​```kotlin
// fix example
​```
```

- Keep each section to 3 sentences or fewer.
- Put code/config values in backticks or a code block, never inline in prose.
- Add a blank line before and after each subheading (`**Problem**`, `**Evidence**`,
  `**Fix direction**`).

### Severity criteria

- **BLOCKER**: must be fixed before merge (security vulnerability, crash, possible data loss)
- **HIGH**: fix strongly recommended (logic error, wrong result)
- **MEDIUM**: fix recommended (maintainability concern, stability risk, transaction boundary
  issue)
- **LOW**: optional improvement (naming, idiom, style)

## Project Convention Checklist (`CLAUDE.md` 강제 관례)

아래 항목은 이 저장소의 **강제 관례**다. 위반은 근거가 `CLAUDE.md`에 명시돼 있으므로
false positive 판정(단계 5)에서 높은 확신도로 취급한다. 반대로 여기에 없는 취향 문제를
"CLAUDE.md 위반"으로 단정하지 말 것.

### 1. external 포트 규칙 (변동성 큰 외부 연동만)

- [ ] 유스케이스가 주입받는 타입이 **인터페이스(포트)**인가? 구체 어댑터/클라이언트
      (`OpenSearchClient` 등)를 직접 주입받으면 위반. → `MEDIUM` 이상
- [ ] 포트의 command/result에 **벤더 이름·벤더 응답 구조**가 없는가? (벤더 중립 DTO)
- [ ] 쿼리·엔드포인트·모델명·응답 파싱 등 벤더 세부가 `external/<vendor>/` 어댑터 안에만
      갇혀 있는가?
- [ ] 포트는 `모듈/application/port/`에, 어댑터는 `external/<vendor>/`에 있는가?
      (의존 방향 `external → application`)
- [ ] 새 벤더 추가가 **어댑터 파일 + 설정 한 줄**로 끝나는가?
      (`@ConditionalOnProperty`로 선택)
- [ ] **역방향 오탐 주의**: DB(CRUD)·캐시·이벤트는 포트를 **강제하지 않는다**.
      Spring Data Repository 직접 주입, `@Cacheable`, `ApplicationEvent` 직접 사용은
      정상이며 "포트가 없다"고 지적하면 안 된다. 포트 판단은 2문항 —
      (1) 교체 가능성/복수 백엔드가 있나 (2) 테스트에서 갈아끼우고 싶나.

### 2. 실패 처리 (하이브리드)

- [ ] **비즈니스 실패**(컨트롤러가 4xx로 매핑해야 하는 것)는 `Result<T, E>`로 반환하는가?
      유스케이스에서 곧바로 throw하면 위반.
- [ ] **시스템 오류·도메인 불변식 위반**은 throw → `SystemExceptionHandler`로 가는가?
      이런 실패를 `Result`로 감싸면 위반.
- [ ] 단순 조회 "없음"은 nullable(`T?`)로 표현하는가?
- [ ] 컨트롤러가 `.getOrThrow()`로 수렴시키는가? 컨트롤러에서 `Result`를 수동 분기해
      응답을 직접 만들면 위반 (최종 응답 포맷은 ProblemDetail 하나로 통일).

### 3. ErrorCode

- [ ] 모듈 `domain/error/`에 **enum**으로 정의됐는가? (sealed·서비스 nested 금지 —
      core `@ApiErrorCode`/`CommonErrorCode` 인프라와 정합해야 함)
- [ ] 구체 ErrorCode(`BookError` 등)가 `core/`에 들어가 있지 않은가? (도메인 지식 침투)

### 4. 컨트롤러 / DTO

- [ ] 컨트롤러가 **리소스별 1개**인가? HTTP 동사별로 쪼갠 컨트롤러는 위반.
- [ ] 컨트롤러가 얇은가? (서비스가 API DTO를 반환하므로 컨트롤러에 매핑·분기 로직 금지)
- [ ] Request/Response가 **서비스 내부 nested `data class`**인가? 2개 이상 유스케이스가
      공유할 때만 `모듈/dto/`로 추출한다. 단일 유스케이스 전용인데 `dto/`에 있으면 `LOW`.

### 5. 엔티티 / 영속성

- [ ] 엔티티가 **소유 모듈의 `domain/`**에 있는가? (`core/`에 구체 엔티티 금지 —
      core는 `BaseEntity` 같은 골격만)
- [ ] 연관이 **LAZY**인가? `FetchType.EAGER`는 위반.
- [ ] 소프트 삭제 규약을 지키는가? (조회 시 `deletedAtIsNull` 조건 누락은 `HIGH`)
- [ ] 도메인 불변식이 `init { require() }`에 있는가?
- [ ] 모듈 경계를 넘는 연관이 **ID 참조**인가? (같은 애그리거트 내부는 객체 참조 허용)
- [ ] N+1, 트랜잭션 경계(`@Transactional` 누락/범위 과다), 읽기 전용 조회의
      `readOnly = true` 여부를 확인한다.

### 6. 검증 / Kotlin 관례

- [ ] Bean Validation을 쓰는가? 커스텀 `isValid()` 류 수동 검증은 위반.
- [ ] Kotlin data class에 **`@field:`** 타깃이 붙어 있는가? (`@NotBlank`만 쓰면 생성자
      파라미터에만 적용돼 **실제로 동작하지 않음** → `HIGH`)
- [ ] 컨트롤러 파라미터에 `@Valid`가 붙어 있는가?
- [ ] `Optional`·빌더·Lombok식 패턴 대신 nullable 타입·named argument를 쓰는가?
- [ ] `!!` 남용, 불필요한 `lateinit`, 플랫폼 타입 방치가 없는가?

### 7. Swagger

- [ ] 컨트롤러에 `@Tag`, 엔드포인트에 `@Operation`이 있는가?
- [ ] 실패할 수 있는 엔드포인트에 `@ApiErrorCode([XxxError::class])`가 붙어 있는가?

### 8. core (Shared Kernel)

- [ ] `core/`에 새로 추가된 것이 리트머스 3문항을 모두 통과하는가?
      (1) 도메인 지식 0 (2) 거의 안 변함 (3) 정말 여러 모듈이 씀.
      하나라도 NO면 소유 모듈로 내려야 한다.

## Base Procedure (from the original code-review command)

Source: ~/.claude/plugins/marketplaces/claude-plugins-official/plugins/code-review/commands/code-review.md
The original steps below are unchanged. Project-specific instructions for this repo are marked
inline as blockquotes, using one of two tags:

- `[EXTEND]` — an extra constraint layered on top of the step; it does not conflict with what
  the step already says.
- `[OVERRIDE]` — replaces or changes what the step says to do.

> **[EXTEND]** Parse the target repository from `$ARGUMENTS` (format: `owner/repo/pull/number`).
> **[EXTEND]** Assume every tool works correctly (for all agents and
> subagents) — never make exploratory or test tool calls, only call a tool when it's actually
> needed to finish the task.

To do this, follow these steps precisely:

1. Use a Haiku agent to check if the pull request (a) is closed, (b) is a draft, (c) does not need a code review (eg. because it is an automated pull request, or is very simple and obviously ok), or (d) already has a code review from you from earlier. If so, do not proceed.
2. Use another Haiku agent to give you a list of file paths to (but not the contents of) any relevant CLAUDE.md files from the codebase: the root CLAUDE.md file (if one exists), as well as any CLAUDE.md files in the directories whose files the pull request modified
3. Use a Haiku agent to view the pull request, and ask the agent to return a summary of the change
4. Then, launch 5 parallel Sonnet agents to independently code review the change. The agents should do the following, then return a list of issues and the reason each issue was flagged (eg. CLAUDE.md adherence, bug, historical git context, etc.):
   a. Agent #1: Audit the changes to make sure they compily with the CLAUDE.md. Note that CLAUDE.md is guidance for Claude as it writes code, so not all instructions will be applicable during code review.

   > **[EXTEND]** Agent #1에게 위 "Project Convention Checklist" 8개 절을 **그대로 전달**하고
   > 항목별로 점검하게 한다. 특히 체크리스트 1번의 **역방향 오탐 주의**(DB·캐시·이벤트는
   > 포트 불필요)와 6번의 `@field:` 누락은 반드시 확인시킬 것.

   b. Agent #2: Read the file changes in the pull request, then do a shallow scan for obvious bugs. Avoid reading extra context beyond the changes, focusing just on the changes themselves. Focus on large bugs, and avoid small issues and nitpicks. Ignore likely false positives.

   > **[EXTEND]** 이 프로젝트에서 자주 나오는 버그 유형을 함께 보게 한다: 소프트 삭제
   > 조건 누락, 트랜잭션 경계 오류, LAZY 연관 접근에 따른 N+1, `Result` 분기 누락,
   > 페이징 파라미터 미검증.

   c. Agent #3: Read the git blame and history of the code modified, to identify any bugs in light of that historical context
   d. Agent #4: Read previous pull requests that touched these files, and check for any comments on those pull requests that may also apply to the current pull request.
   e. Agent #5: Read code comments in the modified files, and make sure the changes in the pull request comply with any guidance in the comments.

   > **[EXTEND]** Never call `ScheduleWakeup` while waiting on these parallel
   > agents (or the ones in step 5). If any agent hasn't returned yet, call `Bash("sleep 15")`
   > and repeat until all results arrive (up to 10 times). ScheduleWakeup has no CI runtime to
   > resume the session, so it would end the session immediately.

5. For each issue found in #4, launch a parallel Haiku agent that takes the PR, issue description, and list of CLAUDE.md files (from step 2), and returns a score to indicate the agent's level of confidence for whether the issue is real or false positive. To do that, the agent should score each issue on a scale from 0-100, indicating its level of confidence. For issues that were flagged due to CLAUDE.md instructions, the agent should double check that the CLAUDE.md actually calls out that issue specifically. The scale is (give this rubric to the agent verbatim):
   a. 0: Not confident at all. This is a false positive that doesn't stand up to light scrutiny, or is a pre-existing issue.
   b. 25: Somewhat confident. This might be a real issue, but may also be a false positive. The agent wasn't able to verify that it's a real issue. If the issue is stylistic, it is one that was not explicitly called out in the relevant CLAUDE.md.
   c. 50: Moderately confident. The agent was able to verify this is a real issue, but it might be a nitpick or not happen very often in practice. Relative to the rest of the PR, it's not very important.
   d. 75: Highly confident. The agent double checked the issue, and verified that it is very likely it is a real issue that will be hit in practice. The existing approach in the PR is insufficient. The issue is very important and will directly impact the code's functionality, or it is an issue that is directly mentioned in the relevant CLAUDE.md.
   e. 100: Absolutely certain. The agent double checked the issue, and confirmed that it is definitely a real issue, that will happen frequently in practice. The evidence directly confirms this.

   > **[EXTEND]** `docs/architecture.md`만 근거인 지적은 "CLAUDE.md에 명시됨"으로 채점하지
   > 않는다 (근거 문서가 다름). 반대로 위 "Project Convention Checklist"에 있는 항목은
   > CLAUDE.md 강제 관례이므로 근거로 인정한다.

6. Filter out any issues with a score less than 80. If there are no issues that meet this criteria, do not proceed.

   > **[OVERRIDE]** Per "Review principles" above, this project posts every validated issue
   > regardless of severity, including LOW-level style nitpicks. Apply this filter only to
   > drop false positives / unverified issues — never drop an issue for being low severity
   > alone.

7. Use a Haiku agent to repeat the eligibility check from #1, to make sure that the pull request is still eligible for code review.
8. Finally, use the gh bash command to comment back on the pull request with the result. When writing your comment, keep in mind to:
   a. Keep your output brief
   b. Avoid emojis
   c. Link and cite relevant code, files, and URLs

   > **[OVERRIDE]** Replace this step: post inline comments attached to file/line instead of a
   > single summary comment:
   > ```bash
   > REPO="<owner>/<repo>"   # parsed from $ARGUMENTS
   > PR_NUMBER="<number>"    # parsed from $ARGUMENTS
   > COMMIT_SHA=$(gh pr view $PR_NUMBER --repo $REPO --json headRefOid -q .headRefOid)
   >
   > gh api repos/$REPO/pulls/$PR_NUMBER/comments \
   >   -f body="$(cat <<'BODY'
   > [SEVERITY] One-line summary (follow the "Comment format" section above)
   > BODY
   > )" \
   >   -f commit_id="$COMMIT_SHA" \
   >   -f path="src/main/kotlin/org/library/book/application/CreateBookService.kt" \
   >   -F line=LINE_NUMBER \
   >   -f side="RIGHT"
   > ```
   > - Line number: use the new-file line number for lines with a `+` in the diff.
   > - Never post a duplicate comment for the same issue.
   > - If inline posting fails, fall back to a single `gh pr comment` with the full issue list.

Examples of false positives, for steps 4 and 5:

- Pre-existing issues
- Something that looks like a bug but is not actually a bug
- Pedantic nitpicks that a senior engineer wouldn't call out
- Issues that a linter, typechecker, or compiler would catch (eg. missing or incorrect imports, type errors, broken tests, formatting issues, pedantic style issues like newlines). No need to run these build steps yourself -- it is safe to assume that they will be run separately as part of CI.
- General code quality issues (eg. lack of test coverage, general security issues, poor documentation), unless explicitly required in CLAUDE.md
- Issues that are called out in CLAUDE.md, but explicitly silenced in the code (eg. due to a lint ignore comment)
- Changes in functionality that are likely intentional or are directly related to the broader change
- Real issues, but on lines that the user did not modify in their pull request

Project-specific false positives (이 프로젝트에서 지적하면 안 되는 것):

- Repository·`@Cacheable`·`ApplicationEvent`를 직접 쓴 것을 "포트가 없다"고 지적 —
  CLAUDE.md가 명시적으로 허용한다 (포트는 external 변동 지점에만 강제).
- JPA 엔티티가 도메인 역할을 겸하는 것을 "도메인 순수화가 안 됐다"고 지적 —
  기본 방침이 "JPA 엔티티 = 도메인"이다.
- 전면 헥사고날(DB 포트화·이중 모델·매핑 계층) 요구 — 과설계로 명시적으로 배제됐다.
- Request/Response가 서비스 안에 nested인 것을 "DTO를 분리하라"고 지적 — 이게 강제 관례다.
- ErrorCode가 enum인 것을 "sealed class로 바꿔라"고 지적 — enum이 강제 관례다.

Notes:

- Do not check build signal or attempt to build or typecheck the app. These will run separately, and are not relevant to your code review.
- Use `gh` to interact with Github (eg. to fetch a pull request, or to create inline comments), rather than web fetch
- Make a todo list first
- You must cite and link each bug (eg. if referring to a CLAUDE.md, you must link it)
- When linking to code, follow the following format precisely, otherwise the Markdown preview won't render correctly: https://github.com/anthropics/claude-cli-internal/blob/c21d3c10bc8e898b7ac1a2d745bdc9bc4e423afe/package.json#L10-L15
  - Requires full git sha
  - You must provide the full sha. Commands like `https://github.com/owner/repo/blob/$(git rev-parse HEAD)/foo/bar` will not work, since your comment will be directly rendered in Markdown.
  - Repo name must match the repo you're code reviewing
  - # sign after the file name
  - Line range format is L[start]-L[end]
  - Provide at least 1 line of context before and after, centered on the line you are commenting about (eg. if you are commenting about lines 5-6, you should link to `L4-7`)

ARGUMENTS: $ARGUMENTS
