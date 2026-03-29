package io.github.fnzl54.library.config.swagger

import io.github.fnzl54.library.core.presentation.ErrorResponse
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["springdoc.enabled"], havingValue = "true")
class SwaggerConfiguration(
    @Value("\${springdoc.version}") private val version: String,
    @Value("\${spring.application.name}") private val serverName: String,
) {
    @Bean
    fun customOpenAPI(): OpenAPI =
        OpenAPI()
            .info(Info().title("$serverName API").version(version))
            .addSecurityItem(SecurityRequirement().addList("Bearer Authentication"))
            .components(
                Components().addSecuritySchemes(
                    "Bearer Authentication",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                ),
            )

    @Bean
    fun operationCustomizer(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            val apiErrorCode =
                handlerMethod.getMethodAnnotation(ApiErrorCode::class.java)
                    ?: return@OperationCustomizer operation

            val allErrorCodes =
                apiErrorCode.errorCodes
                    .flatMap { it.java.enumConstants?.toList() ?: emptyList() }

            if (allErrorCodes.isEmpty()) {
                return@OperationCustomizer operation
            }

            val groupedByStatus = allErrorCodes.groupBy { it.httpStatus.value() }

            operation.responses = operation.responses ?: ApiResponses()

            groupedByStatus.forEach { (status, errorCodes) ->
                val mediaType = MediaType()
                errorCodes.forEach { errorCode ->
                    val example =
                        Example()
                            .value(ErrorResponse.from(errorCode))
                    mediaType.addExamples(errorCode.name, example)
                }

                val content = Content().addMediaType("application/json", mediaType)
                val apiResponse =
                    ApiResponse()
                        .description("Error Response")
                        .content(content)

                operation.responses.addApiResponse(status.toString(), apiResponse)
            }
            operation
        }
}
