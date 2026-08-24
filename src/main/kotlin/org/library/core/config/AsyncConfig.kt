package org.library.core.config

import org.slf4j.MDC
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig {

    @Bean(name = ["bookIndexExecutor"])
    fun bookIndexExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 4
        setQueueCapacity(500)
        setThreadNamePrefix("book-index-")
        setTaskDecorator(mdcTaskDecorator())
        initialize()
    }

    private fun mdcTaskDecorator() = TaskDecorator { runnable ->
        val context = MDC.getCopyOfContextMap()
        Runnable {
            try {
                if (context != null) {
                    MDC.setContextMap(context)
                }
                runnable.run()
            } finally {
                MDC.clear()
            }
        }
    }
}
