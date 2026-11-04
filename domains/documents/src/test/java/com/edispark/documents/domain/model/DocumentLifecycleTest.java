package com.edispark.documents.domain.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentLifecycleTest {

    @Test
    void draftCanBeFinalizedAndThenCancelled() {
        Document document = document();

        document.markDraft();
        document.finalizeDocument();
        document.cancel();

        assertThat(document.lifecycleStatus()).isEqualTo(DocumentLifecycleStatus.CANCELLED);
    }

    @Test
    void cancelledDocumentCannotReceiveAnotherVersion() {
        Document document = document();
        document.cancel();
        Content next = Content.of("next".getBytes(StandardCharsets.UTF_8), Format.JSON);

        assertThatThrownBy(() -> document.addVersion(
                ContentRef.of(next.hash()), next.hash(), "[name]", Format.JSON, null, null, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cancelled documents cannot be changed");
    }

    @Test
    void typedRelationsAreDeduplicated() {
        Document document = document();
        DocumentRelation relation = new DocumentRelation(DocumentRelationType.RESPONDS_TO, DocumentId.generate());

        document.addRelation(relation);
        document.addRelation(relation);

        assertThat(document.relations()).containsExactly(relation);
    }

    private Document document() {
        Content content = Content.of("{}".getBytes(StandardCharsets.UTF_8), Format.JSON);
        return Document.create(
                DocumentType.ORDER_RESPONSE,
                SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("2.1")),
                ContentRef.of(content.hash()),
                content.hash(),
                "[name]",
                Format.JSON,
                null,
                null,
                List.of());
    }
}
