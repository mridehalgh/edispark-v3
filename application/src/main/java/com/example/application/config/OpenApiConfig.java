package com.example.application.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
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
                ));
    }
}
