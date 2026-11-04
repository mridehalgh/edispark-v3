package com.edispark.integrationapplication.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

@Configuration
public class IntegrationConfig {
    @Bean ObjectMapper integrationObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean @Profile("!local & !openapi-export")
    DynamoDbClient integrationDynamoDb(@Value("${integration.aws.region}") String region) {
        return DynamoDbClient.builder().region(Region.of(region)).build();
    }

    @Bean @Profile("!local & !openapi-export")
    EventBridgeClient integrationEventBridge(@Value("${integration.aws.region}") String region) {
        return EventBridgeClient.builder().region(Region.of(region)).build();
    }

    @Bean @Profile("distributed") RestClient documentsRestClient(@Value("${integration.documents-url}") String url) {
        return RestClient.builder().baseUrl(url).requestInterceptor(new BearerPropagationInterceptor()).build();
    }

    @Bean @Profile("distributed") RestClient mftRestClient(@Value("${integration.mft-url}") String url) {
        return RestClient.builder().baseUrl(url).requestInterceptor(new BearerPropagationInterceptor()).build();
    }

}
