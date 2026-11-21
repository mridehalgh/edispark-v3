package com.edispark.documents.infrastructure.storage;

import com.edispark.documents.domain.model.ContentHash;
import com.edispark.documents.domain.repository.DocumentSetRepository;
import com.edispark.documents.domain.repository.SchemaRepository;
import java.util.*;

/** Ownership evidence comes from scoped retained metadata, never from a blob name or a client hash. */
public record RetainedContentInventory(DocumentSetRepository documents, SchemaRepository schemas) {
    public Set<ContentHash> currentTenant() {
        Set<ContentHash> hashes = new HashSet<>();
        documents.findAll().forEach(set -> set.getAllDocuments().forEach(document -> {
            document.versions().forEach(version -> hashes.add(version.contentHash()));
            document.derivatives().forEach(derivative -> hashes.add(derivative.contentHash()));
        }));
        schemas.findAll().forEach(schema -> schema.versions().forEach(version -> hashes.add(version.definitionRef().hash())));
        return Set.copyOf(hashes);
    }
}
