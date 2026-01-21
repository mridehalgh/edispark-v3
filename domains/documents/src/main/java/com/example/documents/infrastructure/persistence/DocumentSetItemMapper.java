package com.example.documents.infrastructure.persistence;

import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.ContentRef;
import com.example.documents.domain.model.Derivative;
import com.example.documents.domain.model.DerivativeId;
import com.example.documents.domain.model.Document;
import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.DocumentVersion;
import com.example.documents.domain.model.DocumentVersionId;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.model.TransformationMethod;
import com.example.documents.domain.model.VersionIdentifier;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.documents.infrastructure.persistence.DynamoDbTableConfig.*;

/**
 * Maps DocumentSet aggregate and its entities to/from DynamoDB items.
 */
public final class DocumentSetItemMapper {

    private final String tenantId;

    public DocumentSetItemMapper() {
        this(DEFAULT_TENANT);
    }

    public DocumentSetItemMapper(String tenantId) {
        this.tenantId = tenantId;
    }

    // ========== DocumentSet Metadata ==========

    /**
     * Creates a DynamoDB item for DocumentSet metadata.
     */
    public Map<String, AttributeValue> toDocumentSetMetadataItem(DocumentSet documentSet) {
        Map<String, AttributeValue> item = new HashMap<>();
        
        String docSetId = documentSet.id().toString();
        item.put(PK, AttributeValue.builder().s(documentSetPk(tenantId, docSetId)).build());
        item.put(SK, AttributeValue.builder().s(documentSetMetadataSk()).build());
        
        item.put("documentSetId", AttributeValue.builder().s(docSetId).build());
        item.put("tenantId", AttributeValue.builder().s(tenantId).build());
        item.put("createdAt", AttributeValue.builder().s(documentSet.createdAt().toString()).build());
        item.put("createdBy", AttributeValue.builder().s(documentSet.createdBy()).build());
        
        if (!documentSet.metadata().isEmpty()) {
            Map<String, AttributeValue> metadataMap = new HashMap<>();
            documentSet.metadata().forEach((k, v) -> 
                metadataMap.put(k, AttributeValue.builder().s(v).build()));
            item.put("metadata", AttributeValue.builder().m(metadataMap).build());
        }
        
        // GSI1 for listing document sets by tenant
        item.put(GSI1_PK, AttributeValue.builder().s(gsi1PkForTenantDocumentSets(tenantId)).build());
        item.put(GSI1_SK, AttributeValue.builder().s(
                gsi1SkForDocumentSet(documentSet.createdAt().toString(), docSetId)).build());
        
        item.put("entityType", AttributeValue.builder().s("DOCUMENT_SET").build());
        
        return item;
    }

    /**
     * Extracts DocumentSet metadata from a DynamoDB item.
     */
    public DocumentSetMetadata fromDocumentSetMetadataItem(Map<String, AttributeValue> item) {
        DocumentSetId id = DocumentSetId.fromString(item.get("documentSetId").s());
        Instant createdAt = Instant.parse(item.get("createdAt").s());
        String createdBy = item.get("createdBy").s();
        
        Map<String, String> metadata = new HashMap<>();
        if (item.containsKey("metadata") && item.get("metadata").m() != null) {
            item.get("metadata").m().forEach((k, v) -> metadata.put(k, v.s()));
        }
        
        return new DocumentSetMetadata(id, createdAt, createdBy, metadata);
    }

    // ========== Document ==========

    /**
     * Creates a DynamoDB item for a Document.
     */
    public Map<String, AttributeValue> toDocumentItem(DocumentSetId documentSetId, Document document) {
        Map<String, AttributeValue> item = new HashMap<>();
        
        String docSetId = documentSetId.toString();
        String docId = document.id().toString();
        
        item.put(PK, AttributeValue.builder().s(documentSetPk(tenantId, docSetId)).build());
        item.put(SK, AttributeValue.builder().s(documentSk(docId)).build());
        
        item.put("documentId", AttributeValue.builder().s(docId).build());
        item.put("documentSetId", AttributeValue.builder().s(docSetId).build());
        item.put("documentType", AttributeValue.builder().s(document.type().name()).build());
        item.put("schemaId", AttributeValue.builder().s(document.schemaRef().schemaId().toString()).build());
        item.put("schemaVersion", AttributeValue.builder().s(document.schemaRef().version().value()).build());
        
        document.relatedDocumentId().ifPresent(relatedId ->
                item.put("relatedDocumentId", AttributeValue.builder().s(relatedId.toString()).build()));
        
        item.put("entityType", AttributeValue.builder().s("DOCUMENT").build());
        
        return item;
    }

    /**
     * Extracts Document data from a DynamoDB item.
     */
    public DocumentData fromDocumentItem(Map<String, AttributeValue> item) {
        DocumentId id = DocumentId.fromString(item.get("documentId").s());
        DocumentType type = DocumentType.valueOf(item.get("documentType").s());
        SchemaId schemaId = SchemaId.fromString(item.get("schemaId").s());
        VersionIdentifier schemaVersion = VersionIdentifier.of(item.get("schemaVersion").s());
        SchemaVersionRef schemaRef = SchemaVersionRef.of(schemaId, schemaVersion);
        
        DocumentId relatedDocumentId = null;
        if (item.containsKey("relatedDocumentId") && item.get("relatedDocumentId").s() != null) {
            relatedDocumentId = DocumentId.fromString(item.get("relatedDocumentId").s());
        }
        
        return new DocumentData(id, type, schemaRef, relatedDocumentId);
    }

    // ========== DocumentVersion ==========

    /**
     * Creates a DynamoDB item for a DocumentVersion.
     */
    public Map<String, AttributeValue> toDocumentVersionItem(
            DocumentSetId documentSetId, 
            DocumentId documentId, 
            DocumentVersion version) {
        Map<String, AttributeValue> item = new HashMap<>();
        
        String docSetId = documentSetId.toString();
        String docId = documentId.toString();
        
        item.put(PK, AttributeValue.builder().s(documentSetPk(tenantId, docSetId)).build());
        item.put(SK, AttributeValue.builder().s(documentVersionSk(docId, version.versionNumber())).build());
        
        item.put("documentVersionId", AttributeValue.builder().s(version.id().toString()).build());
        item.put("documentId", AttributeValue.builder().s(docId).build());
        item.put("documentSetId", AttributeValue.builder().s(docSetId).build());
        item.put("versionNumber", AttributeValue.builder().n(String.valueOf(version.versionNumber())).build());
        item.put("contentHash", AttributeValue.builder().s(version.contentHash().toFullString()).build());
        item.put("contentHashAlgorithm", AttributeValue.builder().s(version.contentHash().algorithm()).build());
        item.put("contentHashValue", AttributeValue.builder().s(version.contentHash().hash()).build());
        item.put("createdAt", AttributeValue.builder().s(version.createdAt().toString()).build());
        item.put("createdBy", AttributeValue.builder().s(version.createdBy()).build());
        
        if (version.previousVersion() != null) {
            item.put("previousVersionId", AttributeValue.builder().s(version.previousVersion().toString()).build());
        }
        
        // GSI2 for content hash lookup
        item.put(GSI2_PK, AttributeValue.builder().s(
                gsi2PkForContentHash(tenantId, version.contentHash().toFullString())).build());
        item.put(GSI2_SK, AttributeValue.builder().s(
                gsi2SkForDocumentVersion(docSetId, docId)).build());
        
        item.put("entityType", AttributeValue.builder().s("DOCUMENT_VERSION").build());
        
        return item;
    }

    /**
     * Extracts DocumentVersion from a DynamoDB item.
     */
    public DocumentVersion fromDocumentVersionItem(Map<String, AttributeValue> item) {
        DocumentVersionId id = DocumentVersionId.fromString(item.get("documentVersionId").s());
        int versionNumber = Integer.parseInt(item.get("versionNumber").n());
        
        String algorithm = item.get("contentHashAlgorithm").s();
        String hashValue = item.get("contentHashValue").s();
        ContentHash contentHash = new ContentHash(algorithm, hashValue);
        ContentRef contentRef = ContentRef.of(contentHash);
        
        Instant createdAt = Instant.parse(item.get("createdAt").s());
        String createdBy = item.get("createdBy").s();
        
        DocumentVersionId previousVersion = null;
        if (item.containsKey("previousVersionId") && item.get("previousVersionId").s() != null) {
            previousVersion = DocumentVersionId.fromString(item.get("previousVersionId").s());
        }
        
        return DocumentVersion.reconstitute(id, versionNumber, contentRef, contentHash, createdAt, createdBy, previousVersion);
    }

    // ========== Derivative ==========

    /**
     * Creates a DynamoDB item for a Derivative.
     */
    public Map<String, AttributeValue> toDerivativeItem(
            DocumentSetId documentSetId, 
            DocumentId documentId, 
            Derivative derivative) {
        Map<String, AttributeValue> item = new HashMap<>();
        
        String docSetId = documentSetId.toString();
        String docId = documentId.toString();
        String derId = derivative.id().toString();
        
        item.put(PK, AttributeValue.builder().s(documentSetPk(tenantId, docSetId)).build());
        item.put(SK, AttributeValue.builder().s(derivativeSk(docId, derId)).build());
        
        item.put("derivativeId", AttributeValue.builder().s(derId).build());
        item.put("documentId", AttributeValue.builder().s(docId).build());
        item.put("documentSetId", AttributeValue.builder().s(docSetId).build());
        item.put("sourceVersionId", AttributeValue.builder().s(derivative.sourceVersionId().toString()).build());
        item.put("targetFormat", AttributeValue.builder().s(derivative.targetFormat().name()).build());
        item.put("contentHash", AttributeValue.builder().s(derivative.contentHash().toFullString()).build());
        item.put("contentHashAlgorithm", AttributeValue.builder().s(derivative.contentHash().algorithm()).build());
        item.put("contentHashValue", AttributeValue.builder().s(derivative.contentHash().hash()).build());
        item.put("transformationMethod", AttributeValue.builder().s(derivative.method().name()).build());
        item.put("createdAt", AttributeValue.builder().s(derivative.createdAt().toString()).build());
        
        item.put("entityType", AttributeValue.builder().s("DERIVATIVE").build());
        
        return item;
    }

    /**
     * Extracts Derivative from a DynamoDB item.
     */
    public Derivative fromDerivativeItem(Map<String, AttributeValue> item) {
        DerivativeId id = DerivativeId.fromString(item.get("derivativeId").s());
        DocumentVersionId sourceVersionId = DocumentVersionId.fromString(item.get("sourceVersionId").s());
        Format targetFormat = Format.valueOf(item.get("targetFormat").s());
        
        String algorithm = item.get("contentHashAlgorithm").s();
        String hashValue = item.get("contentHashValue").s();
        ContentHash contentHash = new ContentHash(algorithm, hashValue);
        ContentRef contentRef = ContentRef.of(contentHash);
        
        TransformationMethod method = TransformationMethod.valueOf(item.get("transformationMethod").s());
        Instant createdAt = Instant.parse(item.get("createdAt").s());
        
        return Derivative.reconstitute(id, sourceVersionId, targetFormat, contentRef, contentHash, method, createdAt);
    }

    // ========== Aggregate Reconstruction ==========

    /**
     * Reconstructs a DocumentSet aggregate from DynamoDB items.
     */
    public DocumentSet reconstructDocumentSet(
            DocumentSetMetadata metadata,
            List<DocumentData> documentDataList,
            Map<DocumentId, List<DocumentVersion>> versionsByDocument,
            Map<DocumentId, List<Derivative>> derivativesByDocument) {
        
        Map<DocumentId, Document> documents = new HashMap<>();
        
        for (DocumentData docData : documentDataList) {
            List<DocumentVersion> versions = versionsByDocument.getOrDefault(docData.id(), List.of());
            List<Derivative> derivatives = derivativesByDocument.getOrDefault(docData.id(), List.of());
            
            Document document = Document.reconstitute(
                    docData.id(),
                    docData.type(),
                    docData.schemaRef(),
                    new ArrayList<>(versions),
                    new ArrayList<>(derivatives),
                    docData.relatedDocumentId());
            
            documents.put(document.id(), document);
        }
        
        return DocumentSet.reconstitute(
                metadata.id(),
                documents,
                metadata.createdAt(),
                metadata.createdBy(),
                metadata.metadata());
    }

    /**
     * Generates all DynamoDB items for a DocumentSet aggregate.
     */
    public List<Map<String, AttributeValue>> toItems(DocumentSet documentSet) {
        List<Map<String, AttributeValue>> items = new ArrayList<>();
        
        // DocumentSet metadata
        items.add(toDocumentSetMetadataItem(documentSet));
        
        // Documents, versions, and derivatives
        for (Document document : documentSet.getAllDocuments()) {
            items.add(toDocumentItem(documentSet.id(), document));
            
            for (DocumentVersion version : document.versions()) {
                items.add(toDocumentVersionItem(documentSet.id(), document.id(), version));
            }
            
            for (Derivative derivative : document.derivatives()) {
                items.add(toDerivativeItem(documentSet.id(), document.id(), derivative));
            }
        }
        
        return items;
    }

    // ========== Helper Records ==========

    public record DocumentSetMetadata(
            DocumentSetId id,
            Instant createdAt,
            String createdBy,
            Map<String, String> metadata) {}

    public record DocumentData(
            DocumentId id,
            DocumentType type,
            SchemaVersionRef schemaRef,
            DocumentId relatedDocumentId) {}
}
