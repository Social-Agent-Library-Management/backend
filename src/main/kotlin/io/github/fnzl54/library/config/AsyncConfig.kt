package io.github.fnzl54.library.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

/** ES 색인 이벤트 리스너(@Async)를 비동기로 실행하기 위한 설정. */
@Configuration
@EnableAsync
class AsyncConfig
