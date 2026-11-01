package com.edispark.integrationapplication.acl;

import com.edispark.exchange.application.DocumentAcl;
import com.edispark.exchange.domain.DocumentReference;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public final class HttpDocumentAcl implements DocumentAcl {
    private final RestClient documents;
    public HttpDocumentAcl(@Qualifier("documentsRestClient") RestClient documents) { this.documents = documents; }

    @Override public DocumentDescriptor describe(UUID setId, UUID documentId) {
        DocumentResponse response = document(setId, documentId);
        Representation representation = canonicalRepresentation(response);
        return new DocumentDescriptor(new DocumentReference(setId, documentId, representation.id(), representation.contentHash()),
                BusinessDocumentType.valueOf(response.type()), "UBL_2_1_JSON", documentId.toString());
    }

    @Override public byte[] content(DocumentReference reference) {
        DocumentResponse response = document(reference.documentSetId(), reference.documentId());
        Representation representation = canonicalRepresentation(response);
        if (!reference.representationId().equals(representation.id())
                || !reference.contentHash().equals(representation.contentHash())) {
            throw new IllegalArgumentException("Document source changed before exchange processing");
        }
        if (representation.derivative()) {
            return documents.get().uri("/api/document-sets/{setId}/documents/{documentId}/derivatives/{derivativeId}/content",
                            reference.documentSetId(), reference.documentId(), representation.id())
                    .retrieve().body(byte[].class);
        }
        return documents.get().uri("/api/document-sets/{setId}/documents/{documentId}/versions/{version}/content",
                        reference.documentSetId(), reference.documentId(), representation.versionNumber())
                    .retrieve().body(byte[].class);
    }

    @Override public DocumentReference retainDerivative(DocumentReference source, String format, byte[] content,
                                                         UUID mappingRevisionId, UUID exchangeId) {
        DocumentResponse response = document(source.documentSetId(), source.documentId());
        VersionResponse version = required(response.currentVersion(), "Document has no current version");
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new NamedBytes(content, "exchange-" + exchangeId + ".edi"), MediaType.APPLICATION_OCTET_STREAM);
        DerivativeResponse derivative = documents.post()
                .uri(builder -> builder.path("/api/document-sets/{setId}/documents/{documentId}/external-derivatives")
                        .queryParam("sourceVersionNumber", version.versionNumber()).queryParam("format", "EDI")
                        .queryParam("variantKey", mappingRevisionId).build(source.documentSetId(), source.documentId()))
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body.build()).retrieve().body(DerivativeResponse.class);
        DerivativeResponse required = required(derivative, "Documents did not return the generated derivative");
        return new DocumentReference(source.documentSetId(), source.documentId(), required.id(), required.contentHash());
    }

    private DocumentResponse document(UUID setId, UUID documentId) {
        return required(documents.get().uri("/api/document-sets/{setId}/documents/{documentId}", setId, documentId)
                .retrieve().body(DocumentResponse.class), "Document was not returned");
    }
    private Representation canonicalRepresentation(DocumentResponse response) {
        VersionResponse version = required(response.currentVersion(), "Document has no current version");
        if ("JSON".equals(version.format())) {
            return new Representation(version.id(), version.contentHash(), version.versionNumber(), false);
        }
        DerivativeResponse derivative = response.derivatives().stream()
                .filter(item -> "JSON".equals(item.targetFormat()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Document has no canonical JSON representation"));
        return new Representation(derivative.id(), derivative.contentHash(), version.versionNumber(), true);
    }
    private static <T> T required(T value, String message) { if (value == null) throw new IllegalArgumentException(message); return value; }
    private record DocumentResponse(UUID id, String type, VersionResponse currentVersion, List<DerivativeResponse> derivatives) { }
    private record VersionResponse(UUID id, int versionNumber, String contentHash, String format) { }
    private record DerivativeResponse(UUID id, UUID sourceVersionId, String targetFormat, String contentHash,
                                      String transformationMethod, String variantKey) { }
    private record Representation(UUID id, String contentHash, int versionNumber, boolean derivative) { }
    private static final class NamedBytes extends ByteArrayResource {
        private final String filename;
        private NamedBytes(byte[] bytes, String filename) { super(bytes); this.filename = filename; }
        @Override public String getFilename() { return filename; }
    }
}
