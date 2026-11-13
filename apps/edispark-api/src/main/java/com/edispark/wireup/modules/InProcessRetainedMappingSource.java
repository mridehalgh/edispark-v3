package com.edispark.wireup.modules;

import com.edispark.exchange.application.DocumentAcl;
import com.edispark.transformations.application.RetainedMappingSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
final class InProcessRetainedMappingSource implements RetainedMappingSource {
    private final DocumentAcl documents;

    @Override public byte[] read(Reference reference, String expectedDocumentType) {
        var descriptor = documents.describe(reference.documentSetId(), reference.documentId());
        var current = descriptor.current();
        if (!current.representationId().equals(reference.representationId())
                || !current.contentHash().equals(reference.contentHash())) {
            throw new com.edispark.transformations.application.RetainedSourceChanged();
        }
        if (!descriptor.type().name().equals(expectedDocumentType)) {
            throw new IllegalArgumentException("The retained document type does not match the mapping.");
        }
        return documents.content(current);
    }
}
