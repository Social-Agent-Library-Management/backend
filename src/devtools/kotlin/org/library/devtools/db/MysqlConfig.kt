package org.library.devtools.db

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.io.File

object MysqlConfig {

    private val dotenv: Map<String, String> = File(".env")
        .takeIf { it.exists() }
        ?.readLines()
        ?.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) return@mapNotNull null
            val (key, value) = trimmed.split("=", limit = 2)
            key.trim() to value.trim()
        }
        ?.toMap()
        ?: emptyMap()

    val jdbcUrl: String = "jdbc:mysql://localhost:3306/${dotenv["DB_NAME"] ?: "library"}" +
        "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&useUnicode=true&rewriteBatchedStatements=true"
    val username: String = requireNotNull(dotenv["DB_USERNAME"]?.takeIf { it.isNotBlank() }) { ".env에 DB_USERNAME이 필요합니다." }
    val password: String = requireNotNull(dotenv["DB_PASSWORD"]?.takeIf { it.isNotBlank() }) { ".env에 DB_PASSWORD가 필요합니다." }

    init {
        val host = java.net.URI(jdbcUrl.removePrefix("jdbc:")).host
        require(host in setOf("localhost", "127.0.0.1")) {
            "devtools 도구는 localhost DB만 대상으로 실행할 수 있습니다. host=$host"
        }
    }

    val jdbcTemplate: JdbcTemplate = JdbcTemplate(
        DriverManagerDataSource(jdbcUrl, username, password).apply { setDriverClassName("com.mysql.cj.jdbc.Driver") },
    )
}
