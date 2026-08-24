package org.library.external.opensearch.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import org.library.external.opensearch.dto.BookIndexDocument
import org.library.external.opensearch.dto.toSourceMap
import org.library.external.opensearch.index.BookIndex
import org.opensearch.client.Request
import org.opensearch.client.ResponseException
import org.opensearch.client.RestClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

private val log = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(name = ["search.engine"], havingValue = "opensearch")
class OpenSearchBookIndexer(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
) {


    fun index(document: BookIndexDocument) {
        val request = Request("PUT", BookIndex.documentPath(document.bookId))
        applyVersionParams(request, document.version)
        request.setJsonEntity(objectMapper.writeValueAsString(document.toSourceMap()))
        perform(request, ignoredStatusCodes = setOf(VERSION_CONFLICT))
    }

    fun delete(bookId: Long, version: Long) {
        val request = Request("DELETE", BookIndex.documentPath(bookId))
        applyVersionParams(request, version)
        perform(request, ignoredStatusCodes = setOf(NOT_FOUND, VERSION_CONFLICT))
    }

    private fun applyVersionParams(request: Request, version: Long) {
        request.addParameter("refresh", "wait_for")
        request.addParameter("version_type", "external_gte")
        request.addParameter("version", version.toString())
    }

    private fun perform(request: Request, ignoredStatusCodes: Set<Int>) {
        try {
            restClient.performRequest(request)
        } catch (e: ResponseException) {
            val statusCode = e.response.statusLine.statusCode
            if (statusCode !in ignoredStatusCodes) throw e
            log.debug { "무시 가능한 색인 응답($statusCode): ${request.method} ${request.endpoint}" }
        }
    }

    companion object {
        private const val NOT_FOUND = 404
        private const val VERSION_CONFLICT = 409
    }
}
