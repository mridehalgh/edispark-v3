package com.edispark.documents.api.rest;

import com.edispark.documents.application.inbound.InboundCanonicalDocumentService;
import com.edispark.documents.domain.model.DocumentType;
import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import io.swagger.v3.oas.annotations.Hidden;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.context.annotation.Profile;

@Hidden
@RestController
@Profile("!openapi-export")
@RequestMapping("/internal/documents/inbound-mft")
public final class InternalInboundDocumentController {
    private final InboundCanonicalDocumentService service;
    private final byte[] expectedToken;

    public InternalInboundDocumentController(InboundCanonicalDocumentService service,
            @Value("${edispark.internal.token}") String token) {
        this.service = service;
        if (token == null || token.isBlank()) throw new IllegalStateException("Internal service token is required");
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InboundCanonicalDocumentService.Result ingest(
            @RequestHeader("X-EdiSpark-Internal-Token") String token,
            @RequestHeader("X-EdiSpark-Tenant-Id") String tenantId,
            @RequestParam("transferId") UUID transferId, @RequestParam("partnerId") UUID partnerId,
            @RequestParam("messageId") String messageId, @RequestParam("filename") String filename,
            @RequestParam("standard") String standard, @RequestParam("messageType") String messageType,
            @RequestParam("documentType") DocumentType documentType,
            @RequestParam("receivedAt") Instant receivedAt,
            @RequestParam("raw") MultipartFile raw, @RequestParam("canonical") MultipartFile canonical)
            throws IOException {
        authenticate(token);
        byte[] rawBytes = raw.getBytes();
        byte[] canonicalBytes = canonical.getBytes();
        AtomicReference<InboundCanonicalDocumentService.Result> result = new AtomicReference<>();
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenantId)), () -> result.set(service.ingest(
                new InboundCanonicalDocumentService.InboundRequest(transferId, partnerId, messageId, filename, standard,
                        messageType, documentType, receivedAt, rawBytes, canonicalBytes))));
        return result.get();
    }

    private void authenticate(String token) {
        if (!MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
