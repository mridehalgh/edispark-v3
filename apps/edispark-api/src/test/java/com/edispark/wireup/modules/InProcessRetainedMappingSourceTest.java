package com.edispark.wireup.modules;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.edispark.exchange.application.DocumentAcl;
import com.edispark.exchange.domain.DocumentReference;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.transformations.application.RetainedMappingSource;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InProcessRetainedMappingSourceTest {
    @Test void readsOnlyTheExactRetainedRepresentationAndMatchingDocumentType() {
        var documents = mock(DocumentAcl.class);
        var source = new InProcessRetainedMappingSource(documents);
        UUID setId = UUID.randomUUID(), documentId = UUID.randomUUID(), versionId = UUID.randomUUID();
        String hash = "sha-256:" + "a".repeat(64);
        var reference = new DocumentReference(setId, documentId, versionId, hash);
        when(documents.describe(setId, documentId)).thenReturn(new DocumentAcl.DocumentDescriptor(reference,
                BusinessDocumentType.ORDER_RESPONSE, "UBL_2_1_JSON", "OR-42", "FINAL", null, null));
        when(documents.content(reference)).thenReturn(new byte[]{1, 2, 3});
        assertThat(source.read(new RetainedMappingSource.Reference(setId, documentId, versionId, hash), "ORDER_RESPONSE")).containsExactly(1, 2, 3);
        clearInvocations(documents);
        assertThatThrownBy(() -> source.read(new RetainedMappingSource.Reference(setId, documentId, versionId, "changed"), "ORDER_RESPONSE"))
                .hasMessageContaining("source changed");
        assertThatThrownBy(() -> source.read(new RetainedMappingSource.Reference(setId, documentId, UUID.randomUUID(), hash), "ORDER_RESPONSE"))
                .hasMessageContaining("source changed");
        assertThatThrownBy(() -> source.read(new RetainedMappingSource.Reference(setId, documentId, versionId, hash), "INVOICE"))
                .hasMessageContaining("type does not match");
        verify(documents, never()).content(any());
    }
}
