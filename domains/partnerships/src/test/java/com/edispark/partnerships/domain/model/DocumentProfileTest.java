package com.edispark.partnerships.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentProfileTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void publishedRevisionIsImmutableAndCreatesANewDraftRevision() {
        DocumentProfile draft = DocumentProfile.draft("tenant", UUID.randomUUID(), "ASN requirements",
                ExchangeDirection.OUTBOUND, BusinessDocumentType.DESPATCH_ADVICE,
                List.of(new DocumentFieldRule("despatch.shipment.handlingUnits[].sscc",
                        FieldRequirement.REQUIRED, null, null, List.of(), null, null, null, null)), NOW);

        DocumentProfile published = draft.publish(NOW.plusSeconds(1));
        DocumentProfile revision = published.newRevision(NOW.plusSeconds(2));

        assertThat(published.status()).isEqualTo(DocumentProfileStatus.PUBLISHED);
        assertThat(revision.status()).isEqualTo(DocumentProfileStatus.DRAFT);
        assertThat(revision.familyId()).isEqualTo(published.familyId());
        assertThat(revision.revision()).isEqualTo(2);
        assertThatThrownBy(() -> published.revise(List.of(), NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void duplicateFieldRulesAreRejected() {
        DocumentFieldRule rule = new DocumentFieldRule("invoice.payment.dueDate", FieldRequirement.REQUIRED,
                null, null, List.of(), null, null, null, null);

        assertThatThrownBy(() -> DocumentProfile.draft("tenant", UUID.randomUUID(), "Invoice requirements",
                ExchangeDirection.OUTBOUND, BusinessDocumentType.INVOICE, List.of(rule, rule), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("field rule keys must be unique");
    }

    @Test
    void invalidFieldConstraintsAreRejectedBeforePublishing() {
        assertThatThrownBy(() -> new DocumentFieldRule("invoice.reference", FieldRequirement.OPTIONAL,
                null, null, List.of(), null, null, "[", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pattern must be a valid regular expression");

        assertThatThrownBy(() -> new DocumentFieldRule("invoice.amount", FieldRequirement.OPTIONAL,
                null, null, List.of(), java.math.BigDecimal.TEN, java.math.BigDecimal.ONE, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("minimum must not exceed maximum");
    }
}
