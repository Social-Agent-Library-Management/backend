package org.library.core.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager =
        CaffeineCacheManager(CacheNames.DASHBOARD_SUMMARY).apply {
            setCaffeine(
                Caffeine.newBuilder()
                    .expireAfterWrite(Duration.ofMinutes(5))
                    .maximumSize(1),
            )
        }

    object CacheNames {
        const val DASHBOARD_SUMMARY = "dashboardSummary"
    }
}
