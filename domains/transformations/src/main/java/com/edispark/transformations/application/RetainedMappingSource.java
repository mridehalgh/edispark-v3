package com.edispark.transformations.application;

import java.util.UUID;

/** Tenant-authorized retained content; references bind recovery to the exact representation tested. */
public interface RetainedMappingSource {
    byte[] read(Reference reference, String expectedDocumentType);
    record Reference(UUID documentSetId, UUID documentId, UUID representationId, String contentHash) { }
}
