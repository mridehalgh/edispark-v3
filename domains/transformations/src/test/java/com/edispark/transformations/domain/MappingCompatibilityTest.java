package com.edispark.transformations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MappingCompatibilityTest {
    private static final UUID UBL_ORDER = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac10");
    private static final UUID EDIFACT_ORDER = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac12");
    private static final UUID TRADACOMS_INVOICE = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac15");
    private static final UUID UBL_INVOICE = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac11");

    @Test
    void describesAnInboundEdifactOrderWithoutRelyingOnItsDisplayName() {
        MappingRevision mapping = mapping(EDIFACT_ORDER, UBL_ORDER, "A name an analyst may safely change");

        assertThat(mapping.compatibility()).isEqualTo(new MappingCompatibility(
                "INBOUND", "ORDER", "EDIFACT", "D96A", "ORDERS"));
    }

    @Test
    void describesAnOutboundTradacomsInvoiceWithoutRelyingOnItsDisplayName() {
        MappingRevision mapping = mapping(UBL_INVOICE, TRADACOMS_INVOICE, "Tenant invoice mapping");

        assertThat(mapping.compatibility()).isEqualTo(new MappingCompatibility(
                "OUTBOUND", "INVOICE", "TRADACOMS", "9", "INVOIC"));
    }

    @Test
    void leavesCustomSchemasUnclassifiedInsteadOfGuessingFromTheName() {
        MappingRevision mapping = mapping(UUID.randomUUID(), UUID.randomUUID(),
                "Inbound EDIFACT order that is not actually compatible");

        assertThat(mapping.compatibility()).isNull();
    }

    private MappingRevision mapping(UUID source, UUID target, String name) {
        return new MappingRevision(UUID.randomUUID(), "tenant", name, 1, source, target,
                MappingStatus.DRAFT, List.of(), null, Instant.parse("2026-11-04T12:00:00Z"), null);
    }
}
