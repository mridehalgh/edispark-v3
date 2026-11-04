package com.edispark.wireup.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    @Bean
    OpenAPI edisparkOpenApi() {
        return new OpenAPI()
                .info(new Info().title("EdiSpark API").version("1.0.0")
                        .description("Documents, managed file transfer, trading partners, mappings, and EDI exchanges"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    OpenApiCustomizer nullableReferenceCustomizer() {
        return openApi -> {
            Schema<?> transferResponse = openApi.getComponents().getSchemas().get("TransferResponse");
            if (transferResponse == null || transferResponse.getProperties() == null) {
                return;
            }

            Schema<?> provenance = transferResponse.getProperties().get("provenance");
            if (provenance == null || provenance.get$ref() == null) {
                return;
            }

            Schema<?> reference = new Schema<>().$ref(provenance.get$ref());
            provenance.set$ref(null);
            provenance.setType(null);
            provenance.setTypes(null);
            provenance.setOneOf(List.of(reference, new Schema<>().types(Set.of("null"))));
        };
    }

    @Bean
    GroupedOpenApi documentsApi() {
        return GroupedOpenApi.builder()
                .group("documents")
                .pathsToMatch("/api/document-sets/**", "/api/schemas/**", "/internal/documents/**")
                .build();
    }

    @Bean
    GroupedOpenApi mftApi(OpenApiCustomizer nullableReferenceCustomizer) {
        return GroupedOpenApi.builder()
                .group("mft")
                .pathsToMatch("/api/mft/**", "/internal/mft/**", "/as2/**")
                .addOpenApiCustomizer(nullableReferenceCustomizer)
                .build();
    }

    @Bean
    GroupedOpenApi integrationApi() {
        return GroupedOpenApi.builder()
                .group("integration")
                .pathsToMatch("/api/trading-partners/**", "/api/mappings/**", "/api/exchanges/**")
                .build();
    }
}
