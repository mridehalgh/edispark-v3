# Design Document

## Overview

The Documents bounded context provides a domain model for managing business documents throughout their lifecycle. It handles document versioning, schema conformance, format transformations, and derivative relationships. The design follows DDD tactical patterns with DocumentSet as the aggregate root, ensuring consistency across related documents, their versions, and derivatives.

The domain is designed to be format-agnostic and extensible, supporting various document standards (UBL, EDIFACT, custom schemas) through a pluggable schema and transformation architecture.

## Architecture

The module follows hexagonal architecture with clear separation between domain, application, and infrastructure layers:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              API Layer                                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐             │
│  │ DocumentController│  │ SchemaController │  │ TransformController│         │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘             │
└───────────┼─────────────────────┼─────────────────────┼─────────────────────┘
            │                     │                     │
┌───────────┼─────────────────────┼─────────────────────┼─────────────────────┐
│           ▼                     ▼                     ▼                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      Application Layer                               │   │
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐   │   │
│  │  │ DocumentCommands │  │ SchemaCommands   │  │ TransformCommands│   │   │
│  │  └──────────────────┘  └──────────────────┘  └──────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│  ┌─────────────────────────────────┼───────────────────────────────────┐   │
│  │                      Domain Layer                                    │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐  │   │
│  │  │ DocumentSet │  │  Document   │  │   Schema    │  │ Derivative │  │   │
│  │  │ (Aggregate) │  │  (Entity)   │  │ (Aggregate) │  │  (Entity)  │  │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └────────────┘  │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                  │   │
│  │  │DocumentVersion│ │SchemaVersion│  │  Content    │                  │   │
│  │  │  (Entity)   │  │  (Entity)   │  │(Value Obj)  │                  │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│  ┌─────────────────────────────────┼───────────────────────────────────┐   │
│  │                    Infrastructure Layer                              │   │
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐   │   │
│  │  │DdbDocumentSetRepo│  │ DdbSchemaRepo    │  │ S3ContentStore   │   │   │
│  │  └──────────────────┘  └──────────────────┘  └──────────────────┘   │   │
│  │  ┌──────────────────┐  ┌──────────────────┐                         │   │
│  │  │ XmlValidator     │  │ JsonValidator    │                         │   │
│  │  └──────────────────┘  └──────────────────┘                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

1. **DocumentSet as Aggregate Root**: Groups related documents (e.g., invoice with credit notes) ensuring transactional consistency.
2. **Immutable Versions**: Document and schema versions are immutable once created, supporting audit trails.
3. **Content-Addressable Storage**: Document content stored in S3 by hash for deduplication and integrity.
4. **DynamoDB for Metadata**: All document and schema metadata stored in DynamoDB for scalability and flexibility.
5. **Pluggable Validators**: Format-specific validators (XML/XSD, JSON/JSON Schema) injected at runtime.
6. **Event-Driven**: Domain events emitted for all significant state changes.
7. **DynamoDB Local for Development**: Uses DynamoDB Local JAR for local development and testing (no TestContainers).

## Components and Interfaces

### Domain Layer

#### Aggregate Roots

**DocumentSet** - The primary aggregate managing related documents:

```java
public class DocumentSet extends AggregateRoot<DocumentSetId> {
    private final DocumentSetId id;
    private final Map<DocumentId, Document> documents;
    private final Instant createdAt;
    private final String createdBy;
    private final Map<String, String> metadata;
    
    public DocumentVersion addDocument(Document document) { ... }
    public DocumentVersion addVersion(DocumentId documentId, Content content) { ... }
    public Derivative createDerivative(DocumentVersionId sourceId, Format targetFormat, Content content) { ... }
    public Document getDocument(DocumentId id) { ... }
    public List<Document> getDocumentsByType(DocumentType type) { ... }
}
```

**Schema** - Aggregate for schema definitions:

```java
public class Schema extends AggregateRoot<SchemaId> {
    private final SchemaId id;
    private final String name;
    private final SchemaFormat format;
    private final List<SchemaVersion> versions;
    
    public SchemaVersion addVersion(VersionIdentifier versionId, Content definition) { ... }
    public SchemaVersion getVersion(VersionIdentifier versionId) { ... }
    public SchemaVersion getLatestVersion() { ... }
}
```

#### Entities

**Document** - A business document within a DocumentSet:

```java
public class Document {
    private final DocumentId id;
    private final DocumentType type;
    private final SchemaVersionRef schemaRef;
    private final List<DocumentVersion> versions;
    private final List<Derivative> derivatives;
    private final DocumentId relatedDocumentId; // optional reference to another document
    
    public DocumentVersion getCurrentVersion() { ... }
    public DocumentVersion getVersion(int versionNumber) { ... }
}
```

**DocumentVersion** - An immutable version of a document:

```java
public class DocumentVersion {
    private final DocumentVersionId id;
    private final int versionNumber;
    private final ContentRef contentRef;
    private final ContentHash contentHash;
    private final Instant createdAt;
    private final String createdBy;
    private final DocumentVersionId previousVersion; // null for first version
}
```

**SchemaVersion** - An immutable version of a schema:

```java
public class SchemaVersion {
    private final SchemaVersionId id;
    private final VersionIdentifier versionIdentifier;
    private final ContentRef definitionRef;
    private final Instant createdAt;
    private final boolean deprecated;
}
```

**Derivative** - A transformed representation:

```java
public class Derivative {
    private final DerivativeId id;
    private final DocumentVersionId sourceVersionId;
    private final Format targetFormat;
    private final ContentRef contentRef;
    private final ContentHash contentHash;
    private final TransformationMethod method;
    private final Instant createdAt;
}
```

#### Value Objects

```java
public record DocumentSetId(UUID value) {}
public record DocumentId(UUID value) {}
public record DocumentVersionId(UUID value) {}
public record SchemaId(UUID value) {}
public record SchemaVersionId(UUID value) {}
public record DerivativeId(UUID value) {}
public record ContentHash(String algorithm, String hash) {}
public record ContentRef(ContentHash hash) {}
public record SchemaVersionRef(SchemaId schemaId, VersionIdentifier version) {}
public record VersionIdentifier(String value) {} // e.g., "2.1.0"

public enum DocumentType { INVOICE, ORDER, CREDIT_NOTE, DEBIT_NOTE, QUOTATION, /* ... */ }
public enum Format { XML, JSON, PDF, EDI }
public enum SchemaFormat { XSD, JSON_SCHEMA, RELAXNG }
public enum TransformationMethod { XSLT, PROGRAMMATIC, EXTERNAL_SERVICE }
```

**Content** - Represents document or schema content:

```java
public record Content(byte[] data, Format format, ContentHash hash) {
    public Content {
        Objects.requireNonNull(data);
        Objects.requireNonNull(format);
        if (hash == null) {
            hash = computeHash(data);
        }
    }
    
    private static ContentHash computeHash(byte[] data) {
        // SHA-256 hash computation
    }
}
```

**ValidationResult** - Outcome of document validation:

```java
public record ValidationResult(
    boolean valid,
    List<ValidationError> errors,
    List<ValidationWarning> warnings
) {
    public static ValidationResult success() {
        return new ValidationResult(true, List.of(), List.of());
    }
    
    public static ValidationResult failure(List<ValidationError> errors) {
        return new ValidationResult(false, errors, List.of());
    }
}

public record ValidationError(String path, String message, String code) {}
public record ValidationWarning(String path, String message) {}
```

### Domain Events

```java
public record DocumentSetCreated(
    DocumentSetId documentSetId,
    String createdBy,
    Instant occurredAt
) implements DomainEvent {}

public record DocumentAdded(
    DocumentSetId documentSetId,
    DocumentId documentId,
    DocumentType type,
    SchemaVersionRef schemaRef,
    Instant occurredAt
) implements DomainEvent {}

public record DocumentVersionAdded(
    DocumentSetId documentSetId,
    DocumentId documentId,
    DocumentVersionId versionId,
    int versionNumber,
    ContentHash contentHash,
    Instant occurredAt
) implements DomainEvent {}

public record DerivativeCreated(
    DocumentSetId documentSetId,
    DocumentId documentId,
    DerivativeId derivativeId,
    DocumentVersionId sourceVersionId,
    Format targetFormat,
    Instant occurredAt
) implements DomainEvent {}

public record DocumentValidated(
    DocumentSetId documentSetId,
    DocumentId documentId,
    DocumentVersionId versionId,
    boolean valid,
    int errorCount,
    Instant occurredAt
) implements DomainEvent {}

public record SchemaVersionCreated(
    SchemaId schemaId,
    SchemaVersionId versionId,
    VersionIdentifier version,
    Instant occurredAt
) implements DomainEvent {}
```

### Repository Interfaces (Ports)

```java
public interface DocumentSetRepository {
    Optional<DocumentSet> findById(DocumentSetId id);
    List<DocumentSet> findAll();
    void save(DocumentSet documentSet);
    void delete(DocumentSetId id);
    Optional<DocumentSet> findByContentHash(ContentHash hash);
}

public interface SchemaRepository {
    Optional<Schema> findById(SchemaId id);
    void save(Schema schema);
    boolean isVersionReferenced(SchemaVersionRef ref);
}

public interface ContentStore {
    void store(Content content);
    Optional<byte[]> retrieve(ContentHash hash);
    boolean exists(ContentHash hash);
    void delete(ContentHash hash);
}
```

### Infrastructure: DynamoDB Single-Table Design

Following the single-table-per-bounded-context pattern, the Documents domain uses one DynamoDB table for all entity types.

**Table Name**: `{service-name}-documents-{environment}` (e.g., `myapp-documents-dev`)

**Multi-Tenancy Preparation**: All partition keys are prefixed with `tenantId` to support future multi-tenant scenarios. For now, use a default tenant ID (e.g., `DEFAULT`).

**Base Table Schema**:

| Attribute | Type | Description |
|-----------|------|-------------|
| PK | String | Partition key - `{tenantId}#{entityKey}` |
| SK | String | Sort key - varies by entity type |

**Entity Key Patterns**:

| Entity | PK | SK |
|--------|----|----|
| DocumentSet metadata | `TENANT#{tenantId}#DOCSET#{documentSetId}` | `METADATA` |
| Document | `TENANT#{tenantId}#DOCSET#{documentSetId}` | `DOC#{documentId}` |
| DocumentVersion | `TENANT#{tenantId}#DOCSET#{documentSetId}` | `DOC#{documentId}#VER#{versionNumber}` |
| Derivative | `TENANT#{tenantId}#DOCSET#{documentSetId}` | `DOC#{documentId}#DER#{derivativeId}` |
| Schema metadata | `TENANT#{tenantId}#SCHEMA#{schemaId}` | `METADATA` |
| SchemaVersion | `TENANT#{tenantId}#SCHEMA#{schemaId}` | `VER#{versionIdentifier}` |

**Global Secondary Indexes (GSIs)**:

**GSI1 - TenantDocumentSetsIndex** (List all document sets for a tenant):
| Attribute | Type | Description |
|-----------|------|-------------|
| GSI1PK | String | `TENANT#{tenantId}#DOCSETS` |
| GSI1SK | String | `{createdAt}#{documentSetId}` (for ordering) |

**GSI2 - ContentHashIndex** (Duplicate detection by content hash):
| Attribute | Type | Description |
|-----------|------|-------------|
| GSI2PK | String | `TENANT#{tenantId}#HASH#{contentHash}` |
| GSI2SK | String | `{documentSetId}#{documentId}` |

**Access Patterns Supported**:

| Access Pattern | Solution |
|----------------|----------|
| Get DocumentSet by ID | Base table: PK = `TENANT#{tenantId}#DOCSET#{documentSetId}`, SK = `METADATA` |
| List all DocumentSets | GSI1: GSI1PK = `TENANT#{tenantId}#DOCSETS` |
| Get all documents in a set | Base table: PK = `TENANT#{tenantId}#DOCSET#{documentSetId}`, SK begins_with `DOC#` |
| Get document with versions | Base table: PK = `TENANT#{tenantId}#DOCSET#{documentSetId}`, SK begins_with `DOC#{documentId}` |
| Get specific version | Base table: PK = `TENANT#{tenantId}#DOCSET#{documentSetId}`, SK = `DOC#{documentId}#VER#{versionNumber}` |
| Get derivatives for document | Base table: PK = `TENANT#{tenantId}#DOCSET#{documentSetId}`, SK begins_with `DOC#{documentId}#DER#` |
| Find duplicates by hash | GSI2: GSI2PK = `TENANT#{tenantId}#HASH#{contentHash}` |
| Get schema by ID | Base table: PK = `TENANT#{tenantId}#SCHEMA#{schemaId}`, SK = `METADATA` |
| Get schema versions | Base table: PK = `TENANT#{tenantId}#SCHEMA#{schemaId}`, SK begins_with `VER#` |

**Item Examples** (using `DEFAULT` tenant):

```json
// DocumentSet metadata
{
    "PK": "TENANT#DEFAULT#DOCSET#docset-123",
    "SK": "METADATA",
    "documentSetId": "docset-123",
    "tenantId": "DEFAULT",
    "createdAt": "2024-01-15T10:30:00Z",
    "createdBy": "user-456",
    "metadata": {"project": "acme", "department": "finance"},
    "GSI1PK": "TENANT#DEFAULT#DOCSETS",
    "GSI1SK": "2024-01-15T10:30:00Z#docset-123"
}

// Document within set
{
    "PK": "TENANT#DEFAULT#DOCSET#docset-123",
    "SK": "DOC#doc-789",
    "documentId": "doc-789",
    "documentType": "INVOICE",
    "schemaId": "ubl-invoice",
    "schemaVersion": "2.1.0",
    "relatedDocumentId": null
}

// DocumentVersion
{
    "PK": "TENANT#DEFAULT#DOCSET#docset-123",
    "SK": "DOC#doc-789#VER#1",
    "documentVersionId": "ver-001",
    "versionNumber": 1,
    "contentHash": "sha256:abc123...",
    "createdAt": "2024-01-15T10:30:00Z",
    "createdBy": "user-456",
    "previousVersionId": null,
    "GSI2PK": "TENANT#DEFAULT#HASH#sha256:abc123...",
    "GSI2SK": "docset-123#doc-789"
}

// Schema metadata
{
    "PK": "TENANT#DEFAULT#SCHEMA#ubl-invoice",
    "SK": "METADATA",
    "schemaId": "ubl-invoice",
    "name": "UBL Invoice Schema",
    "schemaFormat": "JSON_SCHEMA"
}

// SchemaVersion
{
    "PK": "TENANT#DEFAULT#SCHEMA#ubl-invoice",
    "SK": "VER#2.1.0",
    "schemaVersionId": "sv-001",
    "versionIdentifier": "2.1.0",
    "contentHash": "sha256:def456...",
    "createdAt": "2024-01-01T00:00:00Z",
    "deprecated": false
}
```

### Infrastructure: S3 Content Storage

**Bucket Structure**:
```
documents-content-bucket/
├── documents/
│   └── {contentHash}           # Document content by hash
└── schemas/
    └── {contentHash}           # Schema definition content by hash
```

**S3 Configuration**:
- Content-addressable storage using SHA-256 hash as key
- Server-side encryption (SSE-S3)
- Versioning disabled (content is immutable, identified by hash)
- Lifecycle rules for orphaned content cleanup

### DynamoDB Local Configuration

For local development and testing, use DynamoDB Local as a Maven dependency:

```xml
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>DynamoDBLocal</artifactId>
    <version>2.2.1</version>
    <scope>test</scope>
</dependency>
```

**Local S3 Alternative**: For local development, use a file-system based implementation of ContentStore.

### Service Interfaces (Ports)

```java
public interface DocumentValidator {
    ValidationResult validate(Content document, Content schema);
    boolean supports(Format documentFormat, SchemaFormat schemaFormat);
}

public interface DocumentTransformer {
    Content transform(Content source, Format targetFormat);
    boolean supports(Format sourceFormat, Format targetFormat);
}
```

### Application Layer Commands

```java
public record CreateDocumentSetCommand(
    DocumentType initialDocumentType,
    SchemaVersionRef schemaRef,
    Content initialContent,
    String createdBy,
    Map<String, String> metadata
) {}

public record AddDocumentCommand(
    DocumentSetId documentSetId,
    DocumentType type,
    SchemaVersionRef schemaRef,
    Content content,
    String createdBy,
    DocumentId relatedDocumentId // optional
) {}

public record AddVersionCommand(
    DocumentSetId documentSetId,
    DocumentId documentId,
    Content content,
    String createdBy
) {}

public record CreateDerivativeCommand(
    DocumentSetId documentSetId,
    DocumentId documentId,
    int sourceVersionNumber,
    Format targetFormat
) {}

public record ValidateDocumentCommand(
    DocumentSetId documentSetId,
    DocumentId documentId,
    int versionNumber
) {}

public record CreateSchemaCommand(
    String name,
    SchemaFormat format
) {}

public record AddSchemaVersionCommand(
    SchemaId schemaId,
    VersionIdentifier version,
    Content definition
) {}
```

## Data Models

### Package Structure

```
domains/documents/
├── pom.xml
├── DOMAIN.md
└── src/main/java/com/example/documents/
    ├── domain/
    │   ├── model/
    │   │   ├── DocumentSet.java          # Aggregate root
    │   │   ├── DocumentSetId.java
    │   │   ├── Document.java             # Entity
    │   │   ├── DocumentId.java
    │   │   ├── DocumentVersion.java      # Entity
    │   │   ├── DocumentVersionId.java
    │   │   ├── Derivative.java           # Entity
    │   │   ├── DerivativeId.java
    │   │   ├── Schema.java               # Aggregate root
    │   │   ├── SchemaId.java
    │   │   ├── SchemaVersion.java        # Entity
    │   │   ├── SchemaVersionId.java
    │   │   ├── Content.java              # Value object
    │   │   ├── ContentHash.java          # Value object
    │   │   ├── ContentRef.java           # Value object
    │   │   ├── SchemaVersionRef.java     # Value object
    │   │   ├── VersionIdentifier.java    # Value object
    │   │   ├── ValidationResult.java     # Value object
    │   │   ├── ValidationError.java      # Value object
    │   │   ├── DocumentType.java         # Enum
    │   │   ├── Format.java               # Enum
    │   │   ├── SchemaFormat.java         # Enum
    │   │   └── TransformationMethod.java # Enum
    │   ├── event/
    │   │   ├── DocumentSetCreated.java
    │   │   ├── DocumentAdded.java
    │   │   ├── DocumentVersionAdded.java
    │   │   ├── DerivativeCreated.java
    │   │   ├── DocumentValidated.java
    │   │   └── SchemaVersionCreated.java
    │   ├── repository/
    │   │   ├── DocumentSetRepository.java
    │   │   ├── SchemaRepository.java
    │   │   └── ContentStore.java
    │   └── service/
    │       ├── DocumentValidator.java
    │       └── DocumentTransformer.java
    ├── application/
    │   ├── command/
    │   │   ├── CreateDocumentSetCommand.java
    │   │   ├── AddDocumentCommand.java
    │   │   ├── AddVersionCommand.java
    │   │   ├── CreateDerivativeCommand.java
    │   │   ├── ValidateDocumentCommand.java
    │   │   ├── CreateSchemaCommand.java
    │   │   └── AddSchemaVersionCommand.java
    │   ├── handler/
    │   │   ├── DocumentSetCommandHandler.java
    │   │   └── SchemaCommandHandler.java
    │   └── port/
    │       ├── DocumentQueryPort.java
    │       └── SchemaQueryPort.java
    ├── api/
    │   ├── rest/
    │   │   ├── DocumentController.java
    │   │   ├── SchemaController.java
    │   │   └── DocumentExceptionHandler.java
    │   └── dto/
    │       ├── CreateDocumentSetRequest.java
    │       ├── AddDocumentRequest.java
    │       ├── DocumentSetResponse.java
    │       ├── DocumentResponse.java
    │       ├── SchemaResponse.java
    │       └── ValidationResultResponse.java
    └── infrastructure/
        ├── persistence/
        │   ├── DynamoDbDocumentSetRepository.java
        │   ├── DynamoDbSchemaRepository.java
        │   ├── S3ContentStore.java
        │   └── entity/
        │       ├── DocumentSetItem.java      # DynamoDB item
        │       ├── DocumentItem.java         # DynamoDB item
        │       ├── DocumentVersionItem.java  # DynamoDB item
        │       ├── DerivativeItem.java       # DynamoDB item
        │       ├── SchemaItem.java           # DynamoDB item
        │       └── SchemaVersionItem.java    # DynamoDB item
        ├── validation/
        │   ├── XsdValidator.java
        │   └── JsonSchemaValidator.java
        ├── transformation/
        │   ├── XmlToJsonTransformer.java
        │   └── JsonToXmlTransformer.java
        └── config/
            └── DocumentsModuleConfig.java
```

### Entity Relationships

```
┌─────────────────────────────────────────────────────────────────┐
│                        DocumentSet                               │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ id: DocumentSetId                                        │    │
│  │ createdAt: Instant                                       │    │
│  │ createdBy: String                                        │    │
│  │ metadata: Map<String, String>                            │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                   │
│                              │ 1..*                              │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                      Document                            │    │
│  │  id: DocumentId                                          │    │
│  │  type: DocumentType                                      │    │
│  │  schemaRef: SchemaVersionRef ─────────────────────┐      │    │
│  │  relatedDocumentId: DocumentId (optional)         │      │    │
│  │                              │                    │      │    │
│  │                              │ 1..*               │      │    │
│  │                              ▼                    │      │    │
│  │  ┌───────────────────────────────────────────┐   │      │    │
│  │  │           DocumentVersion                  │   │      │    │
│  │  │  id: DocumentVersionId                     │   │      │    │
│  │  │  versionNumber: int                        │   │      │    │
│  │  │  contentRef: ContentRef ───────────────────┼───┼──┐   │    │
│  │  │  contentHash: ContentHash                  │   │  │   │    │
│  │  │  createdAt: Instant                        │   │  │   │    │
│  │  │  previousVersion: DocumentVersionId        │   │  │   │    │
│  │  └───────────────────────────────────────────┘   │  │   │    │
│  │                              │                    │  │   │    │
│  │                              │ 0..*               │  │   │    │
│  │                              ▼                    │  │   │    │
│  │  ┌───────────────────────────────────────────┐   │  │   │    │
│  │  │             Derivative                     │   │  │   │    │
│  │  │  id: DerivativeId                          │   │  │   │    │
│  │  │  sourceVersionId: DocumentVersionId        │   │  │   │    │
│  │  │  targetFormat: Format                      │   │  │   │    │
│  │  │  contentRef: ContentRef ───────────────────┼───┼──┤   │    │
│  │  │  method: TransformationMethod              │   │  │   │    │
│  │  └───────────────────────────────────────────┘   │  │   │    │
│  └─────────────────────────────────────────────────────┘  │   │    │
└───────────────────────────────────────────────────────────┼───┼────┘
                                                            │   │
┌───────────────────────────────────────────────────────────┼───┼────┐
│                          Schema                           │   │    │
│  ┌─────────────────────────────────────────────────────┐  │   │    │
│  │ id: SchemaId                                         │  │   │    │
│  │ name: String                                         │◄─┘   │    │
│  │ format: SchemaFormat                                 │      │    │
│  └─────────────────────────────────────────────────────┘      │    │
│                              │                                 │    │
│                              │ 1..*                            │    │
│                              ▼                                 │    │
│  ┌─────────────────────────────────────────────────────┐      │    │
│  │               SchemaVersion                          │      │    │
│  │  id: SchemaVersionId                                 │      │    │
│  │  versionIdentifier: VersionIdentifier                │      │    │
│  │  definitionRef: ContentRef ──────────────────────────┼──────┤    │
│  │  createdAt: Instant                                  │      │    │
│  │  deprecated: boolean                                 │      │    │
│  └─────────────────────────────────────────────────────┘      │    │
└────────────────────────────────────────────────────────────────┼────┘
                                                                 │
┌────────────────────────────────────────────────────────────────┼────┐
│                       ContentStore                             │    │
│  ┌─────────────────────────────────────────────────────┐      │    │
│  │                    Content                           │◄─────┘    │
│  │  hash: ContentHash (primary key)                     │           │
│  │  data: byte[]                                        │           │
│  │  format: Format                                      │           │
│  └─────────────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────────────┘
```



## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

Based on the prework analysis, the following correctness properties have been identified. These focus on core behaviours that must hold across all inputs.

### Property 1: Document Identity Stability

*For any* Document within a DocumentSet, creating new versions or derivatives SHALL NOT change the Document's identifier.

**Validates: Requirements 1.1**

### Property 2: Version Immutability

*For any* DocumentVersion, once created, its content hash, version number, and creation timestamp SHALL remain unchanged regardless of subsequent operations on the Document or DocumentSet.

**Validates: Requirements 2.1, 2.7**

### Property 3: Version Sequence Integrity

*For any* Document with multiple versions, the version numbers SHALL form a contiguous sequence starting from 1, and each version (except the first) SHALL reference its predecessor.

**Validates: Requirements 2.2, 2.3, 2.4, 2.5**

### Property 4: Latest Version Retrieval

*For any* Document with N versions, calling getCurrentVersion() SHALL return the version with versionNumber equal to N.

**Validates: Requirements 2.6**

### Property 5: Content Hash Consistency

*For any* Content, computing the hash of the same byte array SHALL always produce the same ContentHash value.

**Validates: Requirements 1.5, 5.7, 8.3**

### Property 6: Content Deduplication

*For any* two Content objects with identical byte arrays, storing both SHALL result in only one copy in the ContentStore, and both SHALL share the same ContentHash.

**Validates: Requirements 8.2, 8.5**

### Property 7: Content Round-Trip

*For any* Content stored in the ContentStore, retrieving by its ContentHash SHALL return byte-for-byte identical data.

**Validates: Requirements 8.4**

### Property 8: Schema Version Immutability

*For any* SchemaVersion, once created, its definition content and version identifier SHALL remain unchanged.

**Validates: Requirements 3.3**

### Property 9: Schema Referential Integrity

*For any* SchemaVersion that is referenced by at least one Document, attempting to delete that SchemaVersion SHALL fail.

**Validates: Requirements 3.6**

### Property 10: Validation Correctness

*For any* valid document (conforming to its schema), validation SHALL return a successful ValidationResult. *For any* invalid document (not conforming to its schema), validation SHALL return a failed ValidationResult with at least one error.

**Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**

### Property 11: Transformation Round-Trip

*For any* document in XML format, transforming to JSON and back to XML SHALL preserve semantic equivalence (same data values, potentially different formatting).

**Validates: Requirements 6.1, 6.2, 6.3**

### Property 12: Derivative Uniqueness

*For any* DocumentVersion and target Format combination, the DocumentSet SHALL contain at most one Derivative.

**Validates: Requirements 7.5**

### Property 13: Derivative Source Linkage

*For any* Derivative, its sourceVersionId SHALL reference an existing DocumentVersion within the same DocumentSet.

**Validates: Requirements 5.1, 5.4, 6.5**

### Property 14: DocumentSet Multi-Type Support

*For any* DocumentSet, it SHALL be possible to add Documents of different DocumentTypes, and all added Documents SHALL be retrievable.

**Validates: Requirements 7.1, 7.2, 7.3, 7.7**

### Property 15: Document Relationship Integrity

*For any* Document with a relatedDocumentId, that ID SHALL reference an existing Document within the same DocumentSet.

**Validates: Requirements 7.8**

### Property 16: Domain Event Emission

*For any* state-changing operation (create DocumentSet, add Document, add Version, create Derivative), the corresponding domain event SHALL be emitted with correct identifiers and timestamp.

**Validates: Requirements 9.1, 9.2, 9.3, 9.5, 9.6**

### Property 17: Content Hash Query Correctness

*For any* query by ContentHash, the returned DocumentSets SHALL contain only documents with versions matching that hash.

**Validates: Requirements 10.4**

## Error Handling

### Domain Exceptions

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `DocumentSetNotFoundException` | DocumentSet ID not found | 404 |
| `DocumentNotFoundException` | Document ID not found within set | 404 |
| `VersionNotFoundException` | Version number not found | 404 |
| `SchemaNotFoundException` | Schema or SchemaVersion not found | 404 |
| `SchemaInUseException` | Attempt to delete referenced schema | 409 |
| `DuplicateDerivativeException` | Derivative already exists for format | 409 |
| `InvalidVersionSequenceException` | Version number out of sequence | 400 |
| `ContentHashMismatchException` | Provided hash doesn't match computed | 400 |
| `ValidationException` | Document fails schema validation | 422 |
| `TransformationException` | Format transformation failed | 422 |
| `InvalidSchemaException` | Schema definition is syntactically invalid | 400 |
| `InvalidDocumentRelationException` | Related document ID not found in set | 400 |

### Error Response Structure

```java
public record ErrorResponse(
    String code,
    String message,
    String path,
    Instant timestamp,
    List<ErrorDetail> details
) {}

public record ErrorDetail(
    String field,
    String message,
    String code
) {}
```

### Validation Error Handling

```java
// Example validation error response
{
    "code": "VALIDATION_FAILED",
    "message": "Document does not conform to schema",
    "path": "/api/documents/{id}/validate",
    "timestamp": "2024-01-15T10:30:00Z",
    "details": [
        {
            "field": "/Invoice/ID",
            "message": "Required field is missing",
            "code": "REQUIRED_FIELD_MISSING"
        },
        {
            "field": "/Invoice/IssueDate",
            "message": "Invalid date format",
            "code": "INVALID_FORMAT"
        }
    ]
}
```

## Testing Strategy

### Dual Testing Approach

The testing strategy combines:
1. **Unit tests**: Verify specific examples, edge cases, and error conditions
2. **Property-based tests**: Verify universal properties across generated inputs

### Property-Based Testing Framework

**Framework**: jqwik (modern PBT library for Java/JUnit 5)

```xml
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <version>1.8.2</version>
    <scope>test</scope>
</dependency>
```

### Test Configuration

- Minimum 100 iterations per property test
- Each property test references its design document property
- Tag format: `Feature: documents-domain, Property N: [property description]`

### Test Categories

#### Unit Tests

1. **Domain Model Tests**
   - DocumentSet creation and document addition
   - Version sequencing validation
   - Derivative uniqueness enforcement
   - Document relationship validation

2. **Value Object Tests**
   - ContentHash computation
   - VersionIdentifier parsing and validation
   - SchemaVersionRef equality

3. **Error Handling Tests**
   - Missing entity exceptions
   - Constraint violation exceptions
   - Validation failure responses

4. **Edge Case Tests**
   - Empty DocumentSet operations
   - Single version documents
   - Maximum version numbers
   - Large content handling

#### Property-Based Tests

1. **Identity and Immutability Tests**
   - Document identity stability across operations
   - Version immutability verification
   - Schema version immutability

2. **Content Storage Tests**
   - Hash consistency property
   - Deduplication property
   - Round-trip retrieval property

3. **Validation Tests**
   - Valid documents pass validation
   - Invalid documents fail with errors
   - Format-specific validation (XML/XSD, JSON/JSON Schema)

4. **Transformation Tests**
   - XML to JSON round-trip
   - JSON to XML round-trip
   - Semantic preservation

5. **Aggregate Invariant Tests**
   - Version sequence integrity
   - Derivative uniqueness
   - Document relationship integrity

6. **Query Tests**
   - Filter correctness for each query type

### Test File Organisation

```
src/test/java/com/example/documents/
├── domain/
│   ├── model/
│   │   ├── DocumentSetTest.java
│   │   ├── DocumentVersionTest.java
│   │   ├── ContentHashPropertyTest.java
│   │   ├── VersionSequencePropertyTest.java
│   │   └── DerivativeUniquenessPropertyTest.java
│   └── service/
│       ├── ValidationPropertyTest.java
│       └── TransformationRoundTripPropertyTest.java
├── application/
│   └── handler/
│       ├── DocumentSetCommandHandlerTest.java
│       └── SchemaCommandHandlerTest.java
└── infrastructure/
    ├── persistence/
    │   ├── DynamoDbDocumentSetRepositoryTest.java
    │   ├── DynamoDbSchemaRepositoryTest.java
    │   ├── S3ContentStoreTest.java
    │   └── ContentStorePropertyTest.java
    └── validation/
        ├── XsdValidatorTest.java
        └── JsonSchemaValidatorTest.java
```

### DynamoDB Local Test Setup

Tests use DynamoDB Local JAR (not TestContainers) for integration testing:

```java
@BeforeAll
static void setupDynamoDbLocal() {
    // Start DynamoDB Local in-memory
    System.setProperty("sqlite4java.library.path", "native-libs");
    dynamoDbLocal = DynamoDBEmbedded.create().amazonDynamoDB();
    
    // Create tables
    createDocumentSetTable(dynamoDbLocal);
    createSchemaTable(dynamoDbLocal);
}

@AfterAll
static void tearDownDynamoDbLocal() {
    dynamoDbLocal.shutdown();
}
```

### Local S3 Test Setup

For S3 content store testing, use a file-system based implementation:

```java
public class FileSystemContentStore implements ContentStore {
    private final Path basePath;
    
    @Override
    public void store(Content content) {
        Path filePath = basePath.resolve(content.hash().hash());
        Files.write(filePath, content.data());
    }
    
    @Override
    public Optional<byte[]> retrieve(ContentHash hash) {
        Path filePath = basePath.resolve(hash.hash());
        if (Files.exists(filePath)) {
            return Optional.of(Files.readAllBytes(filePath));
        }
        return Optional.empty();
    }
}
```

### Sample Property Test Structure

```java
@Property(tries = 100)
@Label("Feature: documents-domain, Property 5: Content hash consistency")
void contentHashIsConsistent(@ForAll byte[] data) {
    Content content1 = new Content(data, Format.XML, null);
    Content content2 = new Content(data, Format.XML, null);
    
    assertThat(content1.hash()).isEqualTo(content2.hash());
}

@Property(tries = 100)
@Label("Feature: documents-domain, Property 3: Version sequence integrity")
void versionSequenceIsContiguous(@ForAll @IntRange(min = 1, max = 20) int versionCount) {
    DocumentSet set = createDocumentSetWithVersions(versionCount);
    Document doc = set.getDocuments().iterator().next();
    
    List<Integer> versionNumbers = doc.getVersions().stream()
        .map(DocumentVersion::versionNumber)
        .sorted()
        .toList();
    
    assertThat(versionNumbers).isEqualTo(
        IntStream.rangeClosed(1, versionCount).boxed().toList()
    );
}

@Property(tries = 100)
@Label("Feature: documents-domain, Property 11: Transformation round-trip")
void xmlJsonRoundTripPreservesSemantics(@ForAll("validXmlDocuments") Content xmlContent) {
    DocumentTransformer transformer = new CompositeTransformer(
        new XmlToJsonTransformer(),
        new JsonToXmlTransformer()
    );
    
    Content jsonContent = transformer.transform(xmlContent, Format.JSON);
    Content roundTrippedXml = transformer.transform(jsonContent, Format.XML);
    
    assertThat(parseToMap(roundTrippedXml))
        .isEqualTo(parseToMap(xmlContent));
}
```

### Generators for Property Tests

```java
@Provide
Arbitrary<Content> validXmlDocuments() {
    return Arbitraries.of(loadSampleXmlFiles())
        .map(bytes -> new Content(bytes, Format.XML, null));
}

@Provide
Arbitrary<DocumentType> documentTypes() {
    return Arbitraries.of(DocumentType.values());
}

@Provide
Arbitrary<Map<String, String>> metadata() {
    return Arbitraries.maps(
        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
        Arbitraries.strings().ofMaxLength(100)
    ).ofMaxSize(10);
}
```
