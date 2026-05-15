package com.example.documents.application.query;

import java.util.Objects;

import com.example.documents.application.handler.ContentNotFoundException;
import com.example.documents.application.handler.DocumentNotFoundException;
import com.example.documents.application.handler.DocumentSetNotFoundException;
import com.example.documents.application.handler.VersionNotFoundException;
import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentVersion;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.repository.ContentStore;
import com.example.documents.domain.repository.DocumentSetRepository;

public class DocumentContentQueryService {

    private final DocumentSetRepository documentSetRepository;
    private final ContentStore contentStore;

    public DocumentContentQueryService(DocumentSetRepository documentSetRepository, ContentStore contentStore) {
        this.documentSetRepository = Objects.requireNonNull(documentSetRepository);
        this.contentStore = Objects.requireNonNull(contentStore);
    }

    public RetrievedContent getVersionContent(DocumentSetId setId, DocumentId documentId, int versionNumber) {
        DocumentSet documentSet = documentSetRepository.findById(setId)
                .orElseThrow(() -> new DocumentSetNotFoundException(setId));
        var document = documentSet.getDocument(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(setId, documentId));
        DocumentVersion version = document.getVersion(versionNumber)
                .orElseThrow(() -> new VersionNotFoundException(documentId, versionNumber));
        byte[] bytes = contentStore.retrieve(version.contentHash())
                .orElseThrow(() -> new ContentNotFoundException(version.contentHash()));

        return new RetrievedContent(
                bytes,
                version.format(),
                version.contentHash().toFullString(),
                contentTypeFor(version.format()),
                fileNameFor(documentSet, documentId, versionNumber, version.format()));
    }

    private String contentTypeFor(Format format) {
        return switch (format) {
            case XML -> "application/xml";
            case JSON -> "application/json";
            case PDF -> "application/pdf";
            case EDI -> "application/edi";
        };
    }

    private String fileNameFor(DocumentSet documentSet, DocumentId documentId, int versionNumber, Format format) {
        String sourceFileName = documentSet.metadata().get("sourceFileName");
        if (sourceFileName != null && !sourceFileName.isBlank()) {
            return sourceFileName;
        }
        return documentId.value() + "-v" + versionNumber + extensionFor(format);
    }

    private String extensionFor(Format format) {
        return switch (format) {
            case XML -> ".xml";
            case JSON -> ".json";
            case PDF -> ".pdf";
            case EDI -> ".edi";
        };
    }
}
