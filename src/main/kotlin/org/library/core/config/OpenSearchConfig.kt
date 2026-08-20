package org.library.core.config

import org.apache.http.HttpHost
import org.opensearch.client.RestClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["search.engine"], havingValue = "opensearch")
class OpenSearchConfig(
    @Value("\${opensearch.host:localhost}") private val host: String,
    @Value("\${opensearch.port:9200}") private val port: Int,
) {

    @Bean(destroyMethod = "close")
    fun openSearchRestClient(): RestClient =
        RestClient.builder(HttpHost(host, port, "http")).build()
}
