package com.edispark.wireup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.models.GroupedOpenApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest(properties = {
    "spring.main.lazy-initialization=true",
    "edispark.internal.token=test-internal-token",
    "documents.dynamodb.table-name=test-documents"
})
@ActiveProfiles({"local", "openapi-export"})
class EdiSparkApplicationContextTest {
    @Autowired ApplicationContext context;

    @Test
    void loadsEveryBoundedContextIntoOneApplicationContext() {
        assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(1);
        assertThat(context.getBeansOfType(OpenAPI.class)).hasSize(1);
        assertThat(context.getBeansOfType(GroupedOpenApi.class)).containsOnlyKeys(
                "documentsApi", "mftApi", "integrationApi");
        assertThat(context.getBeansOfType(RestClient.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("spring.application.name")).isEqualTo("edispark-api");
        assertThat(context.containsBean("documentSetCommandHandler")).isTrue();
        assertThat(context.containsBean("mftService")).isTrue();
        assertThat(context.containsBean("mappingService")).isTrue();
        assertThat(context.containsBean("partnershipService")).isTrue();
        assertThat(context.containsBean("exchangeService")).isTrue();
    }
}
