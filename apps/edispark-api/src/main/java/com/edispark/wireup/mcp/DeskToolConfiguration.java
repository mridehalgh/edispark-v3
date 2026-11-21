package com.edispark.wireup.mcp;

import com.edispark.integrationapplication.inbound.InboundEdiTranslator;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import java.util.UUID;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.regions.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
class DeskToolConfiguration {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "edispark.agent.enabled", havingValue = "true")
    static class Agent {
        @Bean
        DeskGuardrail deskGuardrail(Environment environment, @Value("${edispark.agent.guardrail:}") String id,
                                   @Value("${edispark.agent.version:}") String version) {
            if (id.isBlank() && !(environment.acceptsProfiles(Profiles.of("local & !production")))) {
                throw new IllegalStateException("A published Bedrock guardrail is required for the Desk agent");
            }
            return new DeskGuardrail(id, version);
        }

        @Bean
        DeskUsage deskUsage(@Qualifier("integrationDynamoDb") DynamoDbClient db,
                            @Value("${edispark.agent.usagetable:edispark-desk-usage-local}") String table,
                            @Value("${edispark.agent.monthlycredits:}") String credits,
                            @Value("${edispark.agent.monthlycents:500}") long legacyCents,
                            @Value("${edispark.agent.roundsperminute:30}") int perMinute) {
            return new DeskUsage(db, table, DeskUsage.configuredAllowance(credits, legacyCents), perMinute, Clock.systemUTC());
        }

        private ClientOverrideConfiguration.Builder clientSettings() {
            return ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(45))
                    .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build());
        }

        @Bean
        BedrockRuntimeClient deskEvidenceClient() {
            return BedrockRuntimeClient.builder().region(Region.EU_WEST_2).overrideConfiguration(clientSettings().build()).build();
        }

        @Bean
        BedrockRuntimeAsyncClient deskStreamingClient(DeskGuardrail guardrail) {
            return BedrockRuntimeAsyncClient.builder().region(Region.EU_WEST_2)
                    .overrideConfiguration(clientSettings().addExecutionInterceptor(guardrail).build()).build();
        }

        @Bean
        ChatModel deskModel(BedrockRuntimeClient deskEvidenceClient, BedrockRuntimeAsyncClient deskStreamingClient) {
            return BedrockProxyChatModel.builder().region(Region.EU_WEST_2)
                    .bedrockRuntimeClient(deskEvidenceClient).bedrockRuntimeAsyncClient(deskStreamingClient).build();
        }
    }

    @Bean
    McpTools mcpTools(McpExchanges exchanges, McpDocuments documents, McpMappings mappings,
                      McpPartnerships partnerships, McpMft mft,
                      InboundEdiTranslator inbound,
                      ObjectMapper json, MeterRegistry registry, @Value("${edispark.mcp.access-secret:}") String secret) {
        var tools = new McpTools(json, registry);
        var cursor = new McpCursor(secret.isBlank() ? UUID.randomUUID().toString() : secret);
        McpToolCatalog.register(tools, cursor, exchanges, documents, mappings, partnerships, mft, inbound);
        return tools;
    }
}
