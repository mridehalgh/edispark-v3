package com.edispark.integrationapplication.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Component
@Profile("production")
@Slf4j
public final class SqsInboundMftEventConsumer {
    private final SqsClient sqs;
    private final ObjectMapper mapper;
    private final InboundMftEventProcessor processor;
    private final String queueUrl;

    public SqsInboundMftEventConsumer(SqsClient sqs, ObjectMapper mapper, InboundMftEventProcessor processor,
            @Value("${integration.inbound.queue-url}") String queueUrl) {
        if (queueUrl == null || queueUrl.isBlank()) throw new IllegalStateException("Inbound MFT queue URL is required");
        this.sqs = sqs;
        this.mapper = mapper;
        this.processor = processor;
        this.queueUrl = queueUrl;
    }

    @Scheduled(fixedDelayString = "${integration.inbound.poll-delay-ms:1000}")
    public void poll() {
        var response = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl)
                .maxNumberOfMessages(10).waitTimeSeconds(20).visibilityTimeout(60)
                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT).build());
        response.messages().forEach(message -> {
            try {
                var envelope = mapper.readTree(message.body());
                InboundMftPayloadEvent event = mapper.treeToValue(envelope.required("detail"),
                        InboundMftPayloadEvent.class);
                processor.process(event);
                sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle()).build());
            } catch (Exception exception) {
                log.error("Inbound MFT event failed messageId={} receiveCount={} outcome=retry",
                        message.messageId(), message.attributes()
                                .getOrDefault(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "unknown"),
                        exception);
            }
        });
    }

    @Configuration
    @Profile("production")
    static class SqsConfiguration {
        @Bean
        SqsClient integrationSqs(@Value("${integration.aws.region}") String region) {
            return SqsClient.builder().region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.builder().build()).build();
        }
    }
}
