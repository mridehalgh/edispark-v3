# Implementation Plan: Documents Domain

## Overview

This plan implements the Documents bounded context as a new Maven module following DDD tactical patterns. The implementation uses DynamoDB for metadata storage (single-table design) and S3 for content storage, with DynamoDB Local for testing.

## Tasks

- [x] 1. Set up Maven module and project structure
  - [x] 1.1 Create `domains/documents` Maven module with pom.xml
    - Add dependencies: AWS SDK v2 (DynamoDB, S3), Jackson, Jakarta Validation, jqwik
    - Configure DynamoDB Local dependency for testing
    - _Requirements: Project setup_
  
  - [x] 1.2 Create DOMAIN.md documentation
    - Write Vision, Overview, and Domain Narrative sections
    - Define Ubiquitous Language glossary
    - Document key aggregates and domain events
    - _Requirements: Domain documentation standards_
  
  - [x] 1.3 Create package structure
    - Create domain/model, domain/event, domain/repository, domain/service packages
    - Create application/command, application/handler packages
    - Create infrastructure/persistence, infrastructure/validation packages
    - _Requirements: DDD module structure_

- [x] 2. Implement core value objects
  - [x] 2.1 Implement identifier value objects
    - Create DocumentSetId, DocumentId, DocumentVersionId, DerivativeId, SchemaId, SchemaVersionId as Java records
    - Include UUID generation factory methods
    - _Requirements: 1.1_
  
  - [x] 2.2 Implement ContentHash and Content value objects
    - Create ContentHash record with algorithm and hash fields
    - Create Content record with data, format, and hash computation
    - Implement SHA-256 hash computation
    - _Requirements: 1.5, 5.7, 8.3_
  
  - [x] 2.3 Write property test for ContentHash consistency
    - **Property 5: Content Hash Consistency**
    - **Validates: Requirements 1.5, 5.7, 8.3**
  
  - [x] 2.4 Implement remaining value objects
    - Create SchemaVersionRef, VersionIdentifier, ContentRef records
    - Create ValidationResult, ValidationError, ValidationWarning records
    - Create enums: DocumentType, Format, SchemaFormat, TransformationMethod
    - _Requirements: 1.3, 1.4, 4.2, 4.3_

- [x] 3. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement domain entities
  - [x] 4.1 Implement DocumentVersion entity
    - Create immutable DocumentVersion with versionNumber, contentRef, contentHash, timestamps
    - Include previousVersion reference for version chain
    - _Requirements: 2.1, 2.2, 2.3, 2.7_
  
  - [x] 4.2 Implement Derivative entity
    - Create Derivative with sourceVersionId, targetFormat, contentRef, transformationMethod
    - _Requirements: 5.1, 5.2, 5.3, 5.7_
  
  - [x] 4.3 Implement Document entity
    - Create Document with id, type, schemaRef, versions list, derivatives list
    - Implement getCurrentVersion() returning latest version
    - Implement getVersion(int) for historical access
    - Implement addVersion() with sequence validation
    - Implement addDerivative() with uniqueness check
    - _Requirements: 1.1, 1.3, 1.4, 2.4, 2.5, 2.6, 5.4, 5.5, 7.5_
  
  - [x] 4.4 Write property test for version sequence integrity
    - **Property 3: Version Sequence Integrity**
    - **Validates: Requirements 2.2, 2.3, 2.4, 2.5**
  
  - [x] 4.5 Write property test for latest version retrieval
    - **Property 4: Latest Version Retrieval**
    - **Validates: Requirements 2.6**

- [x] 5. Implement DocumentSet aggregate root
  - [x] 5.1 Implement DocumentSet aggregate
    - Create DocumentSet with id, documents map, metadata, timestamps
    - Implement addDocument() for adding new documents
    - Implement addVersion() delegating to Document
    - Implement createDerivative() delegating to Document
    - Implement getDocument(), getDocumentsByType()
    - _Requirements: 7.1, 7.2, 7.3, 7.7_
  
  - [x] 5.2 Implement document relationship support
    - Add relatedDocumentId to Document
    - Validate related document exists in same set
    - _Requirements: 7.8_
  
  - [x] 5.3 Write property test for document identity stability
    - **Property 1: Document Identity Stability**
    - **Validates: Requirements 1.1**
  
  - [x] 5.4 Write property test for version immutability
    - **Property 2: Version Immutability**
    - **Validates: Requirements 2.1, 2.7**
  
  - [x] 5.5 Write property test for derivative uniqueness
    - **Property 12: Derivative Uniqueness**
    - **Validates: Requirements 7.5**
  
  - [x] 5.6 Write property test for multi-type support
    - **Property 14: DocumentSet Multi-Type Support**
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.7**

- [x] 6. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement Schema aggregate
  - [x] 7.1 Implement SchemaVersion entity
    - Create immutable SchemaVersion with versionIdentifier, definitionRef, timestamps
    - _Requirements: 3.3, 3.4, 3.5_
  
  - [x] 7.2 Implement Schema aggregate root
    - Create Schema with id, name, format, versions list
    - Implement addVersion() for adding new schema versions
    - Implement getVersion(), getLatestVersion()
    - _Requirements: 3.1, 3.2, 3.3_
  
  - [x] 7.3 Write property test for schema version immutability
    - **Property 8: Schema Version Immutability**
    - **Validates: Requirements 3.3**

- [x] 8. Implement domain events
  - [x] 8.1 Create domain event records
    - Implement DocumentSetCreated, DocumentAdded, DocumentVersionAdded
    - Implement DerivativeCreated, DocumentValidated, SchemaVersionCreated
    - Include identifiers and timestamps in all events
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_
  
  - [x] 8.2 Add event emission to aggregates
    - Add event collection to DocumentSet and Schema
    - Emit events on state changes (addDocument, addVersion, createDerivative)
    - _Requirements: 7.6, 9.1, 9.2, 9.3_
  
  - [x] 8.3 Write property test for domain event emission
    - **Property 16: Domain Event Emission**
    - **Validates: Requirements 9.1, 9.2, 9.3, 9.5, 9.6**

- [x] 9. Implement repository interfaces (ports)
  - [x] 9.1 Create repository interfaces
    - Define DocumentSetRepository with findById, findAll, save, delete, findByContentHash
    - Define SchemaRepository with findById, save, isVersionReferenced
    - Define ContentStore with store, retrieve, exists, delete
    - _Requirements: 8.1, 8.4, 10.1, 10.2, 10.3, 10.4, 10.5_

- [x] 10. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Implement DynamoDB infrastructure
  - [x] 11.1 Create DynamoDB table configuration
    - Define table schema with PK, SK, GSI1, GSI2
    - Create table creation utility for local development
    - Use TENANT# prefix pattern for multi-tenancy preparation
    - _Requirements: Infrastructure design_
  
  - [x] 11.2 Implement DynamoDB item mappers
    - Create mappers for DocumentSet, Document, DocumentVersion, Derivative items
    - Create mappers for Schema, SchemaVersion items
    - Handle PK/SK construction with tenant prefix
    - _Requirements: Infrastructure design_
  
  - [x] 11.3 Implement DynamoDbDocumentSetRepository
    - Implement findById using GetItem
    - Implement findAll using GSI1 Query
    - Implement save using BatchWriteItem for aggregate
    - Implement delete using BatchWriteItem
    - Implement findByContentHash using GSI2 Query
    - _Requirements: 10.1, 10.2, 10.5_
  
  - [x] 11.4 Implement DynamoDbSchemaRepository
    - Implement findById using GetItem and Query
    - Implement save using BatchWriteItem
    - Implement isVersionReferenced using GSI2 Query
    - _Requirements: 3.6_
  
  - [x] 11.5 Write integration tests for DynamoDB repositories
    - Test CRUD operations with DynamoDB Local
    - Test GSI queries for listing and duplicate detection
    - _Requirements: 10.1, 10.2, 10.5_

- [x] 12. Implement content storage infrastructure
  - [x] 12.1 Implement FileSystemContentStore for local development
    - Store content by hash in local directory
    - Implement store, retrieve, exists, delete operations
    - _Requirements: 8.1, 8.2, 8.4, 8.5_
  
  - [x] 12.2 Write property test for content round-trip
    - **Property 7: Content Round-Trip**
    - **Validates: Requirements 8.4**
  
  - [x] 12.3 Write property test for content deduplication
    - **Property 6: Content Deduplication**
    - **Validates: Requirements 8.2, 8.5**

- [x] 13. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 14. Implement validation services (stub implementations)
  - [x] 14.1 Create DocumentValidator interface
    - Define validate(Content document, Content schema) method
    - Define supports(Format, SchemaFormat) method
    - _Requirements: 4.1_
  
  - [x] 14.2 Implement NoOpValidator (stub)
    - Return successful ValidationResult for all inputs
    - Log warning that validation is stubbed
    - _Requirements: 4.4, 4.5 (stubbed for now)_

- [x] 15. Implement transformation services (stub implementations)
  - [x] 15.1 Create DocumentTransformer interface
    - Define transform(Content source, Format targetFormat) method
    - Define supports(Format sourceFormat, Format targetFormat) method
    - _Requirements: 6.1_
  
  - [x] 15.2 Implement NoOpTransformer (stub)
    - Return input content unchanged (identity transformation)
    - Log warning that transformation is stubbed
    - _Requirements: 6.2, 6.3 (stubbed for now)_

- [-] 16. Implement application layer commands and handlers
  - [x] 16.1 Create command records
    - Implement CreateDocumentSetCommand, AddDocumentCommand, AddVersionCommand
    - Implement CreateDerivativeCommand, ValidateDocumentCommand
    - Implement CreateSchemaCommand, AddSchemaVersionCommand
    - _Requirements: Application layer design_
  
  - [x] 16.2 Implement DocumentSetCommandHandler
    - Handle CreateDocumentSetCommand with validation and persistence
    - Handle AddDocumentCommand with schema validation
    - Handle AddVersionCommand with content storage
    - Handle CreateDerivativeCommand with transformation
    - Handle ValidateDocumentCommand with validator selection
    - _Requirements: 1.2, 3.7, 4.1, 4.6, 6.4, 6.5, 6.6_
  
  - [x] 16.3 Implement SchemaCommandHandler
    - Handle CreateSchemaCommand
    - Handle AddSchemaVersionCommand with schema syntax validation
    - _Requirements: 3.7_
  
  - [x] 16.4 Write unit tests for command handlers
    - Test happy paths and error conditions
    - Mock repository and service dependencies
    - _Requirements: All command handling_

- [ ] 17. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 18. Implement domain exceptions
  - [ ] 18.1 Create domain exception classes
    - Implement DocumentSetNotFoundException, DocumentNotFoundException
    - Implement VersionNotFoundException, SchemaNotFoundException
    - Implement SchemaInUseException, DuplicateDerivativeException
    - Implement InvalidVersionSequenceException, ContentHashMismatchException
    - Implement ValidationException, TransformationException
    - _Requirements: Error handling design_

- [ ] 19. Final integration and wiring
  - [ ] 19.1 Create module configuration
    - Create DocumentsModuleConfig for Spring bean wiring
    - Configure DynamoDB client and S3 client beans
    - Wire repositories, validators, and transformers
    - _Requirements: Module integration_
  
  - [ ] 19.2 Update parent pom.xml
    - Add domains/documents module to parent
    - _Requirements: Project structure_

- [ ] 20. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- All tasks are required for comprehensive implementation
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- DynamoDB Local is used for integration testing (no TestContainers)
- FileSystemContentStore is used for local S3 simulation
- Validation and transformation services are stubbed (NoOp implementations) for initial MVP
