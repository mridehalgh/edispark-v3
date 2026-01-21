# Documents Domain

## Vision

The Documents domain provides the foundation for managing business documents throughout their lifecycle in a format-agnostic manner. It enables organisations to maintain complete document histories, ensure schema conformance, and transform documents between formats while preserving semantic integrity. This domain is strategic because reliable document management underpins all business transactions and regulatory compliance. The core principles of immutable versioning, content-addressable storage, and schema-driven validation must be protected from dilution.

## Overview

The Documents bounded context solves the problem of managing business documents (invoices, orders, credit notes) across their full lifecycle. It handles document versioning, schema conformance validation, format transformations, and derivative relationships. The domain supports various document standards including UBL, EDIFACT, and custom schemas through a pluggable architecture.

This domain does NOT cover:
- Document rendering or presentation (PDF generation is a derivative, not core)
- Business workflow orchestration (that belongs to process domains)
- User authentication or authorisation (cross-cutting concern)
- Document content editing (documents are immutable once created)

The Documents domain integrates with other domains by publishing events when documents are created, versioned, or validated. It consumes schema definitions that may be managed externally and provides document content to downstream systems via content-addressable storage.

## Domain Narrative

When a business needs to store a document, they create a DocumentSet to group related documents together. A DocumentSet might contain an invoice along with its associated credit notes, or an order with its responses. Each document within the set has a specific type and must conform to a schema version.

When a user adds a document to a set, the system first validates it against the referenced schema. If validation passes, the system computes a content hash and stores the content separately from the metadata. This content-addressable approach means identical documents are stored only once, regardless of how many times they appear across different document sets.

Documents are never modified in place. When changes are needed, a new version is created. Each version has a sequential number starting from 1, and maintains a reference to its predecessor. This creates an immutable audit trail. The system always knows which version is current and can retrieve any historical version on demand.

Sometimes documents need to exist in multiple formats. A JSON invoice might need an XML representation for a trading partner, or a PDF rendering for human review. These are called derivatives. Each derivative links back to its source version and records how the transformation was performed. The system prevents duplicate derivatives by ensuring only one derivative exists for each source version and target format combination.

Schema management follows similar principles. Schemas have versions, and once a schema version is published, it cannot be changed. Documents reference specific schema versions, not just schemas. This ensures that validation results are reproducible and that documents remain valid even as schemas evolve. The system prevents deletion of schema versions that are still referenced by documents.

When significant events occur, the domain publishes events. A new document set triggers DocumentSetCreated. Adding a version triggers DocumentVersionAdded. Creating a derivative triggers DerivativeCreated. These events allow other systems to react to document lifecycle changes without tight coupling.

## Ubiquitous Language

| Term | Definition |
|------|------------|
| Content | The actual data of a document or schema, stored separately from metadata and identified by its hash |
| Content Hash | A SHA-256 cryptographic hash of content used for integrity verification and deduplication |
| Derivative | A transformed representation of a document in a different format, linked to its source version |
| Document | A business document instance (invoice, order, credit note) that conforms to a specific schema version |
| Document Set | An aggregate containing related documents with their versions and derivatives |
| Document Type | A category of business document (Invoice, Order, CreditNote) defining its semantic purpose |
| Document Version | An immutable revision of a document, identified by a sequential version number |
| Format | The serialisation format of content (XML, JSON, PDF, EDI) |
| Schema | A formal definition of document structure and validation rules |
| Schema Format | The type of schema definition (XSD, JSON Schema, RelaxNG) |
| Schema Version | An immutable revision of a schema, identified by a version identifier |
| Schema Version Ref | A reference to a specific schema version, combining schema ID and version identifier |
| Transformation | The process of converting a document from one format to another while preserving semantic content |
| Validation Result | The outcome of validating a document against its schema, containing errors and warnings |
| Version Identifier | A semantic version string (e.g. "2.1.0") identifying a schema version |

## Key Aggregates

| Aggregate | Responsibility |
|-----------|----------------|
| DocumentSet | Manages a collection of related documents with their versions and derivatives. Enforces version sequencing, derivative uniqueness, and document relationships. Emits domain events on state changes. |
| Schema | Manages schema definitions and their versions. Ensures schema version immutability and prevents deletion of referenced versions. |

## Domain Events

| Event | Meaning |
|-------|---------|
| DocumentSetCreated | A new document set has been created, ready to receive documents |
| DocumentAdded | A new document has been added to an existing document set |
| DocumentVersionAdded | A new version has been added to an existing document |
| DerivativeCreated | A new derivative has been created from a document version |
| DocumentValidated | A document has been validated against its schema, with pass or fail result |
| SchemaVersionCreated | A new version of a schema has been published |

## Integration Points

| Direction | Domain | Mechanism |
|-----------|--------|-----------|
| Publishes to | Any subscriber | Domain events via event bus |
| Consumes from | External schema providers | Schema definitions via ContentStore |
| Provides to | Downstream systems | Document content via ContentStore |
