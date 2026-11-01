package com.edispark.exchange.application;

import com.edispark.exchange.domain.DocumentReference;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import java.util.UUID;

public interface DocumentAcl {
    DocumentDescriptor describe(UUID documentSetId, UUID documentId);
    byte[] content(DocumentReference reference);
    DocumentReference retainDerivative(DocumentReference source, String format, byte[] content,
                                       UUID mappingRevisionId, UUID exchangeId);

    record DocumentDescriptor(DocumentReference current, BusinessDocumentType type, String canonicalFormat,
                              String businessReference) { }
}
