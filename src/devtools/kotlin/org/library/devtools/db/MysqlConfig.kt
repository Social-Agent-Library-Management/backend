package org.library.devtools.db

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource

object MysqlConfig {

    val jdbcUrl: String = System.getenv("DEVTOOLS_DB_URL")
        ?: "jdbc:mysql://localhost:3306/${System.getenv("DB_NAME") ?: "library"}" +
            "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&useUnicode=true&rewriteBatchedStatements=true"
    val username: String = System.getenv("DB_USERNAME") ?: "library_mysql"
    val password: String = System.getenv("DB_PASSWORD") ?: "library_mysql"

    init {
        require("localhost" in jdbcUrl) {
            "devtools 도구는 localhost DB만 대상으로 실행할 수 있습니다. jdbcUrl=$jdbcUrl"
        }
    }

    val jdbcTemplate: JdbcTemplate = JdbcTemplate(
        DriverManagerDataSource(jdbcUrl, username, password).apply { setDriverClassName("com.mysql.cj.jdbc.Driver") },
    )
}
