package com.edispark.mftapplication.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MftOpenApiConfig {
    private static final String TRANSFER_RESPONSE = "TransferResponse";
    private static final String PROVENANCE = "provenance";

    @Bean
    OpenAPI mftOpenApi() {
        return new OpenAPI().info(new Info().title("MFT API").version("1.0.0")
                .description("Tenant-isolated AS2 partner and transfer operations"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    OpenApiCustomizer nullableTransferProvenance() {
        return openApi -> openApi.getComponents().getSchemas().get(TRANSFER_RESPONSE).addProperty(PROVENANCE,
                new ComposedSchema().addOneOfItem(new Schema<>().$ref("#/components/schemas/TransferProvenance"))
                        .addOneOfItem(new Schema<>().types(Set.of("null"))));
    }
}
