package io.github.fnzl54.library.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * JPA Auditing(@CreatedDate/@LastModifiedDate) 설정.
 * @SpringBootApplication에서 분리해, JPA를 로드하지 않는 슬라이스 테스트
 * (@DataElasticsearchTest 등)가 JPA 메타모델을 요구하지 않도록 한다.
 */
@Configuration
@EnableJpaAuditing
class JpaAuditingConfig
