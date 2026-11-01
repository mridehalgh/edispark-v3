package com.edispark.documents.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.documents.application.inbound.InboundCanonicalDocumentService;
import com.edispark.documents.domain.model.DocumentType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class InboundCanonicalDocumentServiceTest {
    @Test
    void repeatedTransferCreatesOneStableDocumentSetWithRawAndCanonicalRepresentations() {
        var repository = new InMemoryDocumentSetRepository();
        var content = new InMemoryContentStore();
        var service = new InboundCanonicalDocumentService(repository, content);
        var request = new InboundCanonicalDocumentService.InboundRequest(
                UUID.fromString("214ed6fd-1870-4e73-b06c-5c70a95ab15d"),
                UUID.fromString("853b78d4-0925-48d0-b9f7-3298d6f23ab4"),
                "<[email]>", "order.edi", "EDIFACT", "ORDERS", DocumentType.ORDER,
                Instant.parse("2026-11-01T12:00:00Z"),
                "UNH+1+ORDERS:D:96A:UN'BGM+220+PO-1'".getBytes(StandardCharsets.US_ASCII),
                "{\"Order\":[{\"ID\":[{\"_\":\"PO-1\"}]}]}".getBytes(StandardCharsets.UTF_8));

        var first = service.ingest(request);
        var replay = service.ingest(request);

        assertThat(replay).isEqualTo(first);
        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findAll().getFirst().getAllDocuments().getFirst().getCurrentVersion().format().name())
                .isEqualTo("EDI");
        assertThat(repository.findAll().getFirst().getAllDocuments().getFirst().derivatives())
                .singleElement().extracting(item -> item.targetFormat().name()).isEqualTo("JSON");
        assertThat(content.size()).isEqualTo(2);
    }
}
