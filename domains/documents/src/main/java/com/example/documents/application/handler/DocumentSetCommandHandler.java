package com.example.documents.application.handler;

import com.example.documents.application.command.AddDocumentCommand;
import com.example.documents.application.command.AddVersionCommand;
import com.example.documents.application.command.CreateDerivativeCommand;
import com.example.documents.application.command.CreateDocumentSetCommand;
import com.example.documents.application.command.StoreSourceDocumentCommand;
import com.example.documents.application.command.ValidateDocumentCommand;
import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.ContentRef;
import com.example.documents.domain.model.Derivative;
import com.example.documents.domain.model.Document;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentVersion;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.Schema;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaFormat;
import com.example.documents.domain.model.SchemaVersion;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.model.TransformationMethod;
import com.example.documents.domain.model.ValidationResult;
import com.example.documents.domain.model.VersionIdentifier;
import com.example.documents.domain.repository.ContentStore;
import com.example.documents.domain.repository.DocumentSetRepository;
import com.example.documents.domain.repository.SchemaRepository;
import com.example.documents.domain.service.DocumentTransformer;
import com.example.documents.domain.service.DocumentValidator;

import java.util.List;
import java.util.Objects;

/**
 * Command handler for DocumentSet aggregate operations.
 * 
 * <p>This handler orchestrates domain operations for document sets, including:
 * <ul>
 *   <li>Creating document sets with initial documents</li>
 *   <li>Adding documents to existing sets</li>
 *   <li>Adding versions to documents</li>
 *   <li>Creating derivatives through transformation</li>
 *   <li>Validating documents against schemas</li>
 * </ul>
 * 
 * <p>Requirements: 1.2, 3.7, 4.1, 4.6, 6.4, 6.5, 6.6</p>
 */
public class DocumentSetCommandHandler {

    private static final SchemaVersionRef SOURCE_SCHEMA_REF = SchemaVersionRef.of(
            SchemaId.fromString("00000000-0000-0000-0000-000000000001"),
            VersionIdentifier.of("source"));

    private final DocumentSetRepository documentSetRepository;
    private final SchemaRepository schemaRepository;
    private final ContentStore contentStore;
    private final List<DocumentValidator> validators;
    private final List<DocumentTransformer> transformers;

    public DocumentSetCommandHandler(
            DocumentSetRepository documentSetRepository,
            SchemaRepository schemaRepository,
            ContentStore contentStore,
            List<DocumentValidator> validators,
            List<DocumentTransformer> transformers) {
        this.documentSetRepository = Objects.requireNonNull(documentSetRepository);
        this.schemaRepository = Objects.requireNonNull(schemaRepository);
        this.contentStore = Objects.requireNonNull(contentStore);
        this.validators = Objects.requireNonNull(validators);
        this.transformers = Objects.requireNonNull(transformers);
    }

    /**
     * Handles the creation of a new DocumentSet with an initial document.
     * 
     * <p>Requirement 1.2: Record creation timestamp and creating user.</p>
     * 
     * @param command the create document set command
     * @return the created DocumentSet
     * @throws SchemaNotFoundException if the referenced schema does not exist
     * @throws SchemaVersionNotFoundException if the referenced schema version does not exist
     */
    public DocumentSet handle(CreateDocumentSetCommand command) {
        // Validate schema exists
        validateSchemaVersionExists(command.schemaRef().schemaId(), command.schemaRef().version());

        // Store content
        contentStore.store(command.initialContent());
        ContentRef contentRef = ContentRef.of(command.initialContent().hash());

        // Create document set with initial document
        DocumentSet documentSet = DocumentSet.createWithDocument(
                command.initialDocumentType(),
                command.schemaRef(),
                contentRef,
                command.initialContent().hash(),
                command.createdBy(),
                command.metadata(),
                command.initialContent().format(),
                null,
                null,
                List.of());

        // Persist
        documentSetRepository.save(documentSet);

        return documentSet;
    }

    /**
     * Handles adding a new document to an existing DocumentSet.
     * 
     * @param command the add document command
     * @return the created Document
     * @throws DocumentSetNotFoundException if the document set does not exist
     * @throws SchemaNotFoundException if the referenced schema does not exist
     * @throws SchemaVersionNotFoundException if the referenced schema version does not exist
     */
    public Document handle(AddDocumentCommand command) {
        // Load document set
        DocumentSet documentSet = findDocumentSetOrThrow(command.documentSetId());

        // Validate schema exists
        validateSchemaVersionExists(command.schemaRef().schemaId(), command.schemaRef().version());

        // Store content
        contentStore.store(command.content());
        ContentRef contentRef = ContentRef.of(command.content().hash());

        // Add document to set
        Document document;
        if (command.relatedDocumentId() != null) {
            document = documentSet.addDocumentWithRelation(
                    command.type(),
                    command.schemaRef(),
                    contentRef,
                    command.content().hash(),
                    command.createdBy(),
                    command.relatedDocumentId());
        } else {
            document = documentSet.addDocument(
                    command.type(),
                    command.schemaRef(),
                    contentRef,
                    command.content().hash(),
                    command.createdBy());
        }

        // Persist
        documentSetRepository.save(documentSet);

        return document;
    }

    /**
     * Handles adding a new version to an existing document.
     * 
     * @param command the add version command
     * @return the created DocumentVersion
     * @throws DocumentSetNotFoundException if the document set does not exist
     * @throws DocumentNotFoundException if the document does not exist
     */
    public DocumentVersion handle(AddVersionCommand command) {
        // Load document set
        DocumentSet documentSet = findDocumentSetOrThrow(command.documentSetId());

        // Verify document exists
        if (!documentSet.containsDocument(command.documentId())) {
            throw new DocumentNotFoundException(command.documentSetId(), command.documentId());
        }

        // Store content
        contentStore.store(command.content());
        ContentRef contentRef = ContentRef.of(command.content().hash());

        // Add version
        DocumentVersion version = documentSet.addVersion(
                command.documentId(),
                contentRef,
                command.content().hash(),
                command.createdBy(),
                command.content().format(),
                null,
                null,
                List.of());

        // Persist
        documentSetRepository.save(documentSet);

        return version;
    }

    /**
     * Handles creating a derivative from a document version.
     * 
     * <p>Requirement 6.4: If transformation fails, return an error with the failure reason.</p>
     * <p>Requirement 6.5: When transformation succeeds, create a Derivative linked to the source.</p>
     * <p>Requirement 6.6: Validate the transformed document against the target schema if available.</p>
     * 
     * @param command the create derivative command
     * @return the created Derivative
     * @throws DocumentSetNotFoundException if the document set does not exist
     * @throws DocumentNotFoundException if the document does not exist
     * @throws VersionNotFoundException if the version does not exist
     * @throws UnsupportedFormatException if no transformer supports the format combination
     */
    public Derivative handle(CreateDerivativeCommand command) {
        // Load document set
        DocumentSet documentSet = findDocumentSetOrThrow(command.documentSetId());

        // Get document
        Document document = documentSet.getDocument(command.documentId())
                .orElseThrow(() -> new DocumentNotFoundException(command.documentSetId(), command.documentId()));

        // Get source version
        DocumentVersion sourceVersion = document.getVersion(command.sourceVersionNumber())
                .orElseThrow(() -> new VersionNotFoundException(command.documentId(), command.sourceVersionNumber()));

        // Retrieve source content
        byte[] sourceData = contentStore.retrieve(sourceVersion.contentRef().hash())
                .orElseThrow(() -> new IllegalStateException(
                        "Content not found for hash: " + sourceVersion.contentRef().hash()));

        // Determine source format from document's schema
        Format sourceFormat = sourceVersion.format();
        Content sourceContent = new Content(sourceData, sourceFormat, sourceVersion.contentHash());

        // Find transformer
        DocumentTransformer transformer = findTransformer(sourceFormat, command.targetFormat());

        // Transform content
        Content transformedContent = transformer.transform(sourceContent, command.targetFormat());

        // Store transformed content
        contentStore.store(transformedContent);
        ContentRef contentRef = ContentRef.of(transformedContent.hash());

        // Create derivative
        Derivative derivative = documentSet.createDerivative(
                command.documentId(),
                sourceVersion.id(),
                command.targetFormat(),
                contentRef,
                transformedContent.hash(),
                TransformationMethod.PROGRAMMATIC);

        // Persist
        documentSetRepository.save(documentSet);

        return derivative;
    }

    /**
     * Handles validating a document version against its schema.
     * 
     * <p>Requirement 4.1: Check conformance against the referenced SchemaVersion.</p>
     * <p>Requirement 4.6: If a document references a non-existent SchemaVersion, return a validation error.</p>
     * 
     * @param command the validate document command
     * @return the validation result
     * @throws DocumentSetNotFoundException if the document set does not exist
     * @throws DocumentNotFoundException if the document does not exist
     * @throws VersionNotFoundException if the version does not exist
     */
    public ValidationResult handle(ValidateDocumentCommand command) {
        // Load document set
        DocumentSet documentSet = findDocumentSetOrThrow(command.documentSetId());

        // Get document
        Document document = documentSet.getDocument(command.documentId())
                .orElseThrow(() -> new DocumentNotFoundException(command.documentSetId(), command.documentId()));

        // Get version
        DocumentVersion version = document.getVersion(command.versionNumber())
                .orElseThrow(() -> new VersionNotFoundException(command.documentId(), command.versionNumber()));

        // Get schema and version
        Schema schema = schemaRepository.findById(document.schemaRef().schemaId())
                .orElseThrow(() -> new SchemaNotFoundException(document.schemaRef().schemaId()));

        SchemaVersion schemaVersion = schema.getVersion(document.schemaRef().version())
                .orElseThrow(() -> new SchemaVersionNotFoundException(document.schemaRef()));

        // Retrieve document content
        byte[] documentData = contentStore.retrieve(version.contentRef().hash())
                .orElseThrow(() -> new IllegalStateException(
                        "Document content not found for hash: " + version.contentRef().hash()));

        // Retrieve schema content
        byte[] schemaData = contentStore.retrieve(schemaVersion.definitionRef().hash())
                .orElseThrow(() -> new IllegalStateException(
                        "Schema content not found for hash: " + schemaVersion.definitionRef().hash()));

        // Determine formats
        Format documentFormat = version.format();
        Content documentContent = new Content(documentData, documentFormat, version.contentHash());
        Content schemaContent = new Content(schemaData, determineSchemaContentFormat(schema.format()), null);

        // Find validator
        DocumentValidator validator = findValidator(documentFormat, schema.format());

        // Validate
        return validator.validate(documentContent, schemaContent);
    }

    private DocumentSet findDocumentSetOrThrow(com.example.documents.domain.model.DocumentSetId documentSetId) {
        return documentSetRepository.findById(documentSetId)
                .orElseThrow(() -> new DocumentSetNotFoundException(documentSetId));
    }

    public DocumentSet handle(StoreSourceDocumentCommand command) {
        contentStore.store(command.content());
        ContentRef contentRef = ContentRef.of(command.content().hash());

        DocumentSet documentSet = DocumentSet.createWithDocument(
                command.documentType(),
                SOURCE_SCHEMA_REF,
                contentRef,
                command.content().hash(),
                command.createdBy(),
                command.metadata(),
                command.content().format(),
                command.parseStatus(),
                command.messageType(),
                command.parseErrors());

        documentSetRepository.save(documentSet);
        return documentSet;
    }

    private void validateSchemaVersionExists(
            com.example.documents.domain.model.SchemaId schemaId,
            com.example.documents.domain.model.VersionIdentifier version) {
        Schema schema = schemaRepository.findById(schemaId)
                .orElseThrow(() -> new SchemaNotFoundException(schemaId));

        if (schema.getVersion(version).isEmpty()) {
            throw new SchemaVersionNotFoundException(
                    com.example.documents.domain.model.SchemaVersionRef.of(schemaId, version));
        }
    }

    private DocumentTransformer findTransformer(Format sourceFormat, Format targetFormat) {
        return transformers.stream()
                .filter(t -> t.supports(sourceFormat, targetFormat))
                .findFirst()
                .orElseThrow(() -> new UnsupportedFormatException(sourceFormat, targetFormat));
    }

    private DocumentValidator findValidator(Format documentFormat, SchemaFormat schemaFormat) {
        return validators.stream()
                .filter(v -> v.supports(documentFormat, schemaFormat))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No validator found for " + documentFormat + " / " + schemaFormat));
    }

    private Format determineSchemaContentFormat(SchemaFormat schemaFormat) {
        return switch (schemaFormat) {
            case XSD, RELAXNG -> Format.XML;
            case JSON_SCHEMA -> Format.JSON;
        };
    }
}
