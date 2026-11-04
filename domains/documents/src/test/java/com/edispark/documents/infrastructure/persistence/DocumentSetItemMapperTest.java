package com.edispark.documents.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.documents.domain.model.Format;
import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.ContentRef;
import com.edispark.documents.domain.model.Document;
import com.edispark.documents.domain.model.DocumentId;
import com.edispark.documents.domain.model.DocumentSetId;
import com.edispark.documents.domain.model.DocumentType;
import com.edispark.documents.domain.model.DocumentRelation;
import com.edispark.documents.domain.model.DocumentRelationType;
import com.edispark.documents.domain.model.SchemaId;
import com.edispark.documents.domain.model.SchemaVersionRef;
import com.edispark.documents.domain.model.VersionIdentifier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class DocumentSetItemMapperTest {

    private final DocumentSetItemMapper mapper = new DocumentSetItemMapper("tenant-test");

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void retainsLocalAndCrossSetRelationsDuringPersistence(boolean crossSet) {
        var content = Content.of(new byte[] {1}, Format.JSON);
        var document = Document.create(DocumentType.INVOICE,
                SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("2.1")),
                ContentRef.of(content.hash()), content.hash(), "local-demo");
        var relation = new DocumentRelation(DocumentRelationType.INVOICES, DocumentId.generate(),
                crossSet ? DocumentSetId.generate() : null);
        document.addRelation(relation);

        var restored = mapper.fromDocumentItem(mapper.toDocumentItem(DocumentSetId.generate(), document));

        assertThat(restored.relations()).containsExactly(relation);
        if (crossSet) assertThat(document.relatedDocumentId()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRADACOMS", "EDIFACT"})
    void restoresLegacyEdiStandardAsEdiFormat(String legacyFormat) {
        var item = documentVersionItem(legacyFormat);

        assertThat(mapper.fromDocumentVersionItem(item).format()).isEqualTo(Format.EDI);
    }

    private Map<String, AttributeValue> documentVersionItem(String format) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("documentVersionId", text(UUID.randomUUID().toString()));
        item.put("versionNumber", AttributeValue.builder().n("1").build());
        item.put("contentHashAlgorithm", text("SHA-256"));
        item.put("contentHashValue", text("fixture-hash"));
        item.put("format", text(format));
        item.put("createdAt", text("2026-11-04T12:00:00Z"));
        item.put("createdBy", text("local-demo"));
        return item;
    }

    private AttributeValue text(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
