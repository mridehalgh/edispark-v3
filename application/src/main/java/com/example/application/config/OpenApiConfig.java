package com.example.application.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

/**
 * OpenAPI configuration for API documentation.
 * Provides Swagger UI at /swagger-ui.html for interactive API testing.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI documentsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Documents API")
                        .description("""
                                RESTful API for managing document sets, schemas, and document versions.
                                
                                ## Features
                                - Create and manage document sets with multiple document types
                                - Version control for documents with immutable versions
                                 - Schema management with versioning support
                                 - Document validation against schemas
                                 - Derivative document creation (transformations)
                                 - Raw document-version content download, including preserved EDI source payloads
                                 - Content-addressable storage with hash verification
                                
                                ## Domain Concepts
                                - **Document Set**: Container for related documents of different types
                                - **Document**: A single document within a set (e.g., invoice, order)
                                - **Document Version**: Immutable snapshot of document content
                                - **Schema**: Defines structure and validation rules for documents
                                - **Derivative**: Transformed version of a document (e.g., PDF from XML)
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development server")
                ))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("OIDC access token containing tenant_id and role claims")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    /**
     * Keeps non-success responses consistent with the API exception handler.
     * Without this, Springdoc infers the successful response model for an
     * {@code @ApiResponse} that only provides a description.
     */
    @Bean
    public OpenApiCustomizer errorResponseContract() {
        return openApi -> {
            openApi.getComponents().addSchemas("ApiError", new ObjectSchema()
                    .addProperty("code", new StringSchema())
                    .addProperty("message", new StringSchema())
                    .addProperty("timestamp", new DateTimeSchema())
                    .addProperty("details", new ObjectSchema().additionalProperties(new ObjectSchema()))
                    .required(List.of("code", "message", "timestamp", "details")));

            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    operation.getResponses().forEach((status, response) -> {
                        if (!status.startsWith("2")) {
                            response.setContent(new Content().addMediaType("application/json", new MediaType()
                                    .schema(new ObjectSchema().$ref("#/components/schemas/ApiError"))));
                        }
                    })));
        };
    }
}
