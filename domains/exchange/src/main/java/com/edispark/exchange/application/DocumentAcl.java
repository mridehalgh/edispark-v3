package com.edispark.exchange.application;

import com.edispark.exchange.domain.DocumentReference;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import java.util.UUID;

public interface DocumentAcl {
    DocumentDescriptor describe(UUID documentSetId, UUID documentId);
    byte[] content(DocumentReference reference);
    DocumentReference retainDerivative(DocumentReference source, GeneratedArtifact artifact, byte[] content,
                                       UUID mappingRevisionId, UUID exchangeId);

    enum GeneratedArtifact {
        EDI_PAYLOAD("EDI"),
        MAPPING_REPORT("JSON");

        private final String documentFormat;

        GeneratedArtifact(String documentFormat) {
            this.documentFormat = documentFormat;
        }

        public String documentFormat() {
            return documentFormat;
        }
    }

    record DocumentDescriptor(DocumentReference current, BusinessDocumentType type, String canonicalFormat,
                              String businessReference) { }
}
