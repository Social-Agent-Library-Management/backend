package org.library.devtools.db

import org.apache.http.HttpHost
import org.opensearch.client.RestClient

object OpenSearchConfig {

    val host: String = System.getenv("OPENSEARCH_HOST") ?: "localhost"
    val port: Int = (System.getenv("OPENSEARCH_PORT") ?: "9200").toInt()

    init {
        require(host == "localhost" || host == "127.0.0.1") {
            "devtools 도구는 localhost OpenSearch만 대상으로 실행할 수 있습니다. host=$host"
        }
    }

    // RestClient는 내부에 non-daemon I/O 스레드를 띄우므로 공유 싱글턴으로 두지 않는다.
    // 호출부가 매번 새로 만들어서 use{}로 직접 닫아야 한다.
    fun newClient(): RestClient = RestClient.builder(HttpHost(host, port, "http")).build()
}
