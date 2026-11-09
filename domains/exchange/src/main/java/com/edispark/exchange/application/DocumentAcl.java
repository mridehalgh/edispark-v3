package com.edispark.exchange.application;

import com.edispark.exchange.domain.DocumentReference;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import java.util.UUID;

public interface DocumentAcl {
    DocumentDescriptor describe(UUID documentSetId, UUID documentId);
    byte[] content(DocumentReference reference);
    default java.util.List<ReadinessIssue> validate(DocumentDescriptor descriptor) {
        return java.util.List.of(ReadinessIssue.configuration("VALIDATION_UNAVAILABLE", "Document validation is not installed."));
    }
    DocumentReference retainDerivative(DocumentReference source, GeneratedArtifact artifact, byte[] content,
                                       UUID mappingRevisionId, UUID exchangeId);

    enum GeneratedArtifact {
        EDI_PAYLOAD("EDI"),
        VALIDATION_REPORT("JSON"),
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
                              String businessReference, String lifecycleStatus, UUID partnerId, UUID connectionId) {
        public DocumentDescriptor(DocumentReference current, BusinessDocumentType type, String canonicalFormat, String businessReference) {
            this(current, type, canonicalFormat, businessReference, "FINAL", null, null);
        }
    }
}
