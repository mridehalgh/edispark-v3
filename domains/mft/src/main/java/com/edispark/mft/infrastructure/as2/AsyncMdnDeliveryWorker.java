package com.edispark.mft.infrastructure.as2;

import com.edispark.mft.application.As2TransportException;
import com.edispark.mft.domain.model.MdnDelivery;
import com.edispark.mft.domain.repository.MftRepository;
import com.edispark.mft.domain.repository.PayloadStore;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public final class AsyncMdnDeliveryWorker {
    private final MftRepository repository;
    private final PayloadStore payloads;
    private final As2EndpointPolicy endpoints;
    private final Clock clock;
    private final MdnTransport transport;
    private final String owner = UUID.randomUUID().toString();

    public AsyncMdnDeliveryWorker(MftRepository repository, PayloadStore payloads,
                                  As2EndpointPolicy endpoints, Clock clock) {
        this(repository, payloads, endpoints, clock, new HttpMdnTransport());
    }

    AsyncMdnDeliveryWorker(MftRepository repository, PayloadStore payloads,
                           As2EndpointPolicy endpoints, Clock clock, MdnTransport transport) {
        this.repository = repository;
        this.payloads = payloads;
        this.endpoints = endpoints;
        this.clock = clock;
        this.transport = transport;
    }

    @Scheduled(fixedDelayString = "${mft.mdn-delivery.poll-interval-ms:1000}")
    public void deliverDue() {
        repository.listPendingMdnDeliveries(clock.instant(), 10).forEach(this::withLease);
    }

    private void withLease(MdnDelivery delivery) {
        if (!repository.tryAcquireMdnLease(delivery.tenantId(), delivery.id(), owner, clock.instant(),
                clock.instant().plus(Duration.ofMinutes(2)))) return;
        try {
            deliver(delivery);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            boolean retryable = !(exception instanceof As2TransportException transport) || transport.retryable();
            repository.saveMdnDeliveryIfAttempts(
                    delivery.failedAttempt(exception.getMessage(), retryable, clock.instant()), delivery.attempts());
            log.warn("AS2 asynchronous MDN delivery failed for delivery {} after {} attempts",
                    delivery.id(), delivery.attempts() + 1);
        } finally {
            repository.releaseMdnLease(delivery.tenantId(), delivery.id(), owner);
        }
    }

    private void deliver(MdnDelivery delivery) throws Exception {
        endpoints.validate(delivery.callback());
        byte[] body;
        try (var input = payloads.get(delivery.tenantId(), delivery.payload())) {
            body = input.readAllBytes();
        }
        transport.send(delivery, body);
        repository.saveMdnDeliveryIfAttempts(delivery.delivered(clock.instant()), delivery.attempts());
    }

    @FunctionalInterface
    interface MdnTransport {
        void send(MdnDelivery delivery, byte[] body) throws Exception;
    }

    private static final class HttpMdnTransport implements MdnTransport {
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();

        @Override
        public void send(MdnDelivery delivery, byte[] body) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(delivery.callback()).timeout(Duration.ofSeconds(30))
                .header("Content-Type", delivery.contentType()).header("AS2-Version", "1.2")
                .header("AS2-From", delivery.as2From()).header("AS2-To", delivery.as2To())
                .header("Message-ID", delivery.messageId())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
                throw new As2TransportException("Partner rejected asynchronous MDN with HTTP "
                        + response.statusCode(), null, retryable);
            }
        }
    }
}
