package org.library.core.swagger

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.library.core.exception.ErrorCode
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ProblemDetail

@Configuration
@ConditionalOnProperty(prefix = "swagger", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI {
        val securityScheme = SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .`in`(SecurityScheme.In.HEADER)
            .name("Authorization")

        return OpenAPI()
            .info(
                Info()
                    .title("Library API")
                    .description("도서 대출 관리 시스템 API")
                    .version("v0.0.1"),
            )
            .addSecurityItem(SecurityRequirement().addList(BEARER))
            .components(Components().addSecuritySchemes(BEARER, securityScheme))
    }

    @Bean
    fun errorCodeOperationCustomizer(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            handlerMethod.getMethodAnnotation(ApiErrorCode::class.java)
                ?.let { documentErrorResponses(operation, it) }
            operation
        }

    private fun documentErrorResponses(operation: Operation, annotation: ApiErrorCode) {
        val responses = operation.responses ?: ApiResponses().also { operation.responses = it }

        selectErrorCodes(annotation)
            .groupBy { it.status.value() }
            .forEach { (status, codes) ->
                responses.addApiResponse(status.toString(), toApiResponse(codes))
            }
    }

    private fun selectErrorCodes(annotation: ApiErrorCode): List<ErrorCode> {
        val all = annotation.errorCodes.flatMap { it.java.enumConstants?.toList().orEmpty() }
        if (annotation.only.isEmpty()) return all

        val byCode = all.associateBy { it.code }
        return annotation.only.map { code ->
            requireNotNull(byCode[code]) {
                "@ApiErrorCode(only)에 존재하지 않는 ErrorCode: $code (사용 가능: ${byCode.keys})"
            }
        }
    }

    private fun toApiResponse(codes: List<ErrorCode>): ApiResponse {
        val mediaType = MediaType()
        codes.forEach { code ->
            mediaType.addExamples(code.code, Example().value(toProblemDetail(code)))
        }
        return ApiResponse()
            .description("에러 응답 (RFC 9457 ProblemDetail)")
            .content(Content().addMediaType("application/problem+json", mediaType))
    }

    private fun toProblemDetail(code: ErrorCode): ProblemDetail =
        ProblemDetail.forStatusAndDetail(code.status, code.message).apply {
            title = code.code
            setProperty("code", code.code)
            setProperty("traceId", "a1b2c3d4")
        }

    companion object {
        private const val BEARER = "BearerToken"
    }
}
