package com.edispark.mft.api.rest;

import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferId;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import com.edispark.identifiers.tenant.TenantId;
import io.swagger.v3.oas.annotations.Hidden;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

@Hidden
@RestController
@Profile("!openapi-export")
@RequestMapping("/internal/mft/transfers")
public final class InternalTransferController {
    private final MftService service;
    private final byte[] expectedToken;

    public InternalTransferController(MftService service,
            @Value("${edispark.internal.token}") String token) {
        this.service = service;
        this.expectedToken = requireToken(token);
    }

    @GetMapping("/{transferId}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable UUID transferId,
            @RequestHeader("X-EdiSpark-Internal-Token") String token,
            @RequestHeader("X-EdiSpark-Tenant-Id") String tenantId) throws IOException {
        authenticate(token);
        AtomicReference<ResponseEntity<InputStreamResource>> response = new AtomicReference<>();
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenantId)), () -> {
            try {
                Transfer transfer = service.getTransfer(new TransferId(transferId));
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(transfer.contentType()));
                headers.setContentLength(transfer.payload().size());
                response.set(new ResponseEntity<>(new InputStreamResource(service.getPayload(transfer.id())), headers,
                        HttpStatus.OK));
            } catch (IOException exception) {
                throw new InternalPayloadReadException(exception);
            }
        });
        return response.get();
    }

    @GetMapping
    public List<InboundTransfer> inbound(@RequestHeader("X-EdiSpark-Internal-Token") String token,
            @RequestHeader("X-EdiSpark-Tenant-Id") String tenantId) {
        authenticate(token);
        AtomicReference<List<InboundTransfer>> response = new AtomicReference<>();
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenantId)), () -> response.set(
                service.listTransfers().stream()
                        .filter(item -> item.direction() == com.edispark.mft.domain.model.TransferDirection.INBOUND)
                        .filter(item -> item.payload() != null)
                        .map(InboundTransfer::from).toList()));
        return response.get();
    }

    private void authenticate(String token) {
        if (!MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    private static byte[] requireToken(String token) {
        if (token == null || token.isBlank()) throw new IllegalStateException("Internal service token is required");
        return token.getBytes(StandardCharsets.UTF_8);
    }

    private static final class InternalPayloadReadException extends RuntimeException {
        private InternalPayloadReadException(IOException cause) { super(cause); }
    }

    public record InboundTransfer(UUID eventId, String tenantId, UUID transferId, UUID partnerId,
            String messageId, String filename, String contentType, String sha256, long size,
            java.time.Instant receivedAt) {
        private static InboundTransfer from(Transfer transfer) {
            UUID eventId = UUID.nameUUIDFromBytes((transfer.tenantId() + ":inbound:" + transfer.id())
                    .getBytes(StandardCharsets.UTF_8));
            return new InboundTransfer(eventId, transfer.tenantId(), transfer.id().value(), transfer.partnerId(),
                    transfer.as2MessageId(), transfer.filename(), transfer.contentType(), transfer.payload().sha256(),
                    transfer.payload().size(), transfer.createdAt());
        }
    }
}
