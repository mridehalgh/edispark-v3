package com.edispark.documents.infrastructure.seed;

import com.edispark.documents.domain.model.*;
import com.edispark.documents.domain.repository.ContentStore;
import com.edispark.documents.domain.repository.DocumentSetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocalSampleDocumentReconcilerTest {
    private final Map<DocumentSetId, DocumentSet> data = new LinkedHashMap<>();
    private final Map<ContentHash, byte[]> payloads = new HashMap<>();
    private final DocumentSetRepository sets = mock(DocumentSetRepository.class);
    private final ContentStore content = mock(ContentStore.class);
    private LocalSampleDocumentReconciler reconciler;

    @BeforeEach
    void setup() {
        when(sets.findAll()).thenAnswer(invocation -> List.copyOf(data.values()));
        doAnswer(invocation -> { DocumentSet set = invocation.getArgument(0); data.put(set.id(), set); return null; })
                .when(sets).save(any());
        when(content.retrieve(any())).thenAnswer(invocation -> Optional.ofNullable(payloads.get(invocation.getArgument(0))));
        reconciler = new LocalSampleDocumentReconciler(sets, content, new ObjectMapper(), "unused-in-unit-test");
    }

    @Test
    void repairsSampleReferencesAndLinksAcrossFilesWithoutDuplicatesOrContentChanges() {
        var order = sample("partner-a", "ORDER", "PO-42", "", true);
        var invoice = sample("partner-a", "INVOICE", "INV-42", "\"OrderReference\":[{\"ID\":[{\"_\":\"PO-42\"}]}],", true);
        var hash = invoice.getAllDocuments().getFirst().getCurrentVersion().contentHash();
        reconciler.reconcileTenantData();
        reconciler.reconcileTenantData();

        var repaired = data.get(invoice.id());
        assertThat(repaired.metadata()).containsEntry("businessDocumentNumber", "INV-42");
        assertThat(repaired.getAllDocuments().getFirst().relations()).singleElement().satisfies(relation -> {
            assertThat(relation.targetDocumentSetId()).isEqualTo(order.id());
            assertThat(relation.type()).isEqualTo(DocumentRelationType.INVOICES);
        });
        assertThat(repaired.getAllDocuments().getFirst().getCurrentVersion().contentHash()).isEqualTo(hash);
        assertThat(data).hasSize(2);
        var document = repaired.getAllDocuments().getFirst();
        List.copyOf(document.relations()).forEach(document::removeRelation);
        reconciler.reconcileTenantData();
        assertThat(data.get(invoice.id()).getAllDocuments().getFirst().relations()).hasSize(1);
    }

    @Test
    void doesNotJoinDifferentPartnerRevisionsOrTouchDeveloperDocuments() {
        sample("other-partner", "ORDER", "PO-42", "", true);
        var invoice = sample("partner-a", "INVOICE", "INV-42", "\"OrderReference\":[{\"ID\":[{\"_\":\"PO-42\"}]}],", true);
        var developer = sample("partner-a", "ORDER", "PO-42", "", false);
        reconciler.reconcileTenantData();
        assertThat(data.get(invoice.id()).getAllDocuments().getFirst().relations()).isEmpty();
        assertThat(data.get(developer.id()).metadata()).doesNotContainKey("businessDocumentNumber");
    }

    @Test
    void leavesAmbiguousReferencesUnlinked() {
        sample("partner-a", "ORDER", "PO-42", "", true);
        sample("partner-a", "ORDER", "PO-42", "", true);
        var invoice = sample("partner-a", "INVOICE", "INV-42", "\"OrderReference\":[{\"ID\":[{\"_\":\"PO-42\"}]}],", true);
        reconciler.reconcileTenantData();
        assertThat(data.get(invoice.id()).getAllDocuments().getFirst().relations()).isEmpty();
    }

    @Test
    void repairsAReferenceWhenItsMatchingSampleArrivesLater() {
        var invoice = sample("partner-a", "INVOICE", "INV-42", "\"OrderReference\":[{\"ID\":[{\"_\":\"PO-42\"}]}],", true);
        reconciler.reconcileTenantData();
        assertThat(data.get(invoice.id()).getAllDocuments().getFirst().relations()).isEmpty();
        var order = sample("partner-a", "ORDER", "PO-42", "", true);

        reconciler.reconcileTenantData();
        reconciler.reconcileTenantData();

        assertThat(data.get(invoice.id()).getAllDocuments().getFirst().relations()).singleElement()
                .satisfies(relation -> assertThat(relation.targetDocumentSetId()).isEqualTo(order.id()));
    }

    private DocumentSet sample(String partner, String type, String number, String fields, boolean owned) {
        String root = type.equals("ORDER") ? "Order" : "Invoice";
        String body = "{\"_D\":\"urn:oasis:names:specification:ubl:schema:xsd:" + root + "-2\",\"" + root
                + "\":[{" + fields + "\"ID\":[{\"_\":\"" + number + "\"}]}]}";
        Content payload = Content.of(body.getBytes(StandardCharsets.UTF_8), Format.JSON);
        payloads.put(payload.hash(), payload.data());
        var set = DocumentSet.createWithDocument(DocumentType.valueOf(type),
                SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("2.1")), ContentRef.of(payload.hash()),
                payload.hash(), owned ? "integration:mft-inbound" : "developer",
                Map.of("mftPartnerId", partner, "as2MessageId", "<local-inbound@northstar.test>"),
                Format.JSON, null, null, List.of());
        data.put(set.id(), set);
        return set;
    }
}
