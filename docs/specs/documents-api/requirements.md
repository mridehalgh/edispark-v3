# Requirements Document

## Introduction

This document specifies the requirements for implementing REST API controllers for the Documents domain module and creating a Spring Boot application module to wire everything together. The API will expose the existing document management functionality (document sets, documents, versions, derivatives, schemas) through RESTful endpoints, following the hexagonal architecture pattern where controllers live within the domain module.

## Glossary

- **Document_Controller**: REST controller handling HTTP requests for document set and document operations
- **Schema_Controller**: REST controller handling HTTP requests for schema operations
- **Exception_Handler**: Component that maps domain exceptions to appropriate HTTP responses
- **Request_DTO**: Data transfer object representing incoming HTTP request payloads
- **Response_DTO**: Data transfer object representing outgoing HTTP response payloads
- **Application_Module**: The Spring Boot application module that orchestrates domain modules
- **Documents_Module**: The existing documents domain module containing domain logic and infrastructure

## Requirements

### Requirement 1: Document Set REST Operations

**User Story:** As an API consumer, I want to manage document sets through REST endpoints, so that I can create and retrieve document sets programmatically.

#### Acceptance Criteria

1. WHEN a client sends a POST request to `/api/document-sets` with valid payload, THE Document_Controller SHALL create a new document set and return 201 Created with the document set details
2. WHEN a client sends a POST request with missing required fields, THE Document_Controller SHALL return 400 Bad Request with validation error details
3. WHEN a client sends a GET request to `/api/document-sets/{id}`, THE Document_Controller SHALL return the document set if found
4. WHEN a client sends a GET request for a non-existent document set, THE Document_Controller SHALL return 404 Not Found
5. THE Response_DTO SHALL include document set ID, creation timestamp, created by, metadata, and list of documents

### Requirement 2: Document Operations within Document Sets

**User Story:** As an API consumer, I want to add and retrieve documents within a document set, so that I can manage related business documents together.

#### Acceptance Criteria

1. WHEN a client sends a POST request to `/api/document-sets/{setId}/documents` with valid payload, THE Document_Controller SHALL add a document to the set and return 201 Created
2. WHEN a client sends a POST request referencing a non-existent document set, THE Document_Controller SHALL return 404 Not Found
3. WHEN a client sends a POST request with an invalid schema reference, THE Document_Controller SHALL return 404 Not Found
4. WHEN a client sends a GET request to `/api/document-sets/{setId}/documents/{docId}`, THE Document_Controller SHALL return the document details
5. WHEN a client sends a GET request for a non-existent document, THE Document_Controller SHALL return 404 Not Found
6. THE Response_DTO SHALL include document ID, type, schema reference, version count, and current version details

### Requirement 3: Document Version Operations

**User Story:** As an API consumer, I want to add and retrieve document versions, so that I can maintain an immutable history of document changes.

#### Acceptance Criteria

1. WHEN a client sends a POST request to `/api/document-sets/{setId}/documents/{docId}/versions` with valid payload, THE Document_Controller SHALL add a new version and return 201 Created
2. WHEN a client sends a POST request for a non-existent document, THE Document_Controller SHALL return 404 Not Found
3. WHEN a client sends a GET request to `/api/document-sets/{setId}/documents/{docId}/versions/{versionNumber}`, THE Document_Controller SHALL return the version details
4. WHEN a client sends a GET request for a non-existent version, THE Document_Controller SHALL return 404 Not Found
5. THE Response_DTO SHALL include version ID, version number, content hash, creation timestamp, and created by

### Requirement 4: Derivative Operations

**User Story:** As an API consumer, I want to create and retrieve document derivatives, so that I can transform documents between formats.

#### Acceptance Criteria

1. WHEN a client sends a POST request to `/api/document-sets/{setId}/documents/{docId}/derivatives` with valid payload, THE Document_Controller SHALL create a derivative and return 201 Created
2. WHEN a client sends a POST request with an unsupported format transformation, THE Document_Controller SHALL return 400 Bad Request
3. WHEN a client sends a POST request that would create a duplicate derivative, THE Document_Controller SHALL return 409 Conflict
4. WHEN a client sends a GET request to `/api/document-sets/{setId}/documents/{docId}/derivatives`, THE Document_Controller SHALL return all derivatives for the document
5. THE Response_DTO SHALL include derivative ID, source version ID, target format, content hash, and transformation method

### Requirement 5: Document Validation Operations

**User Story:** As an API consumer, I want to validate documents against their schemas, so that I can ensure document conformance.

#### Acceptance Criteria

1. WHEN a client sends a POST request to `/api/document-sets/{setId}/documents/{docId}/versions/{versionNumber}/validate`, THE Document_Controller SHALL validate the document and return the result
2. WHEN validation succeeds, THE Document_Controller SHALL return 200 OK with valid=true
3. WHEN validation fails, THE Document_Controller SHALL return 422 Unprocessable Entity with validation errors
4. WHEN the document or version does not exist, THE Document_Controller SHALL return 404 Not Found
5. THE Response_DTO SHALL include valid flag, list of errors with paths and messages, and list of warnings

### Requirement 6: Schema REST Operations

**User Story:** As an API consumer, I want to manage schemas through REST endpoints, so that I can define document validation rules.

#### Acceptance Criteria

1. WHEN a client sends a POST request to `/api/schemas` with valid payload, THE Schema_Controller SHALL create a new schema and return 201 Created
2. WHEN a client sends a POST request with missing required fields, THE Schema_Controller SHALL return 400 Bad Request
3. WHEN a client sends a GET request to `/api/schemas/{id}`, THE Schema_Controller SHALL return the schema if found
4. WHEN a client sends a GET request for a non-existent schema, THE Schema_Controller SHALL return 404 Not Found
5. THE Response_DTO SHALL include schema ID, name, format, and list of versions

### Requirement 7: Schema Version Operations

**User Story:** As an API consumer, I want to add and retrieve schema versions, so that I can evolve schemas while maintaining backward compatibility.

#### Acceptance Criteria

1. WHEN a client sends a POST request to `/api/schemas/{schemaId}/versions` with valid payload, THE Schema_Controller SHALL add a version and return 201 Created
2. WHEN a client sends a POST request for a non-existent schema, THE Schema_Controller SHALL return 404 Not Found
3. WHEN a client sends a POST request with a duplicate version identifier, THE Schema_Controller SHALL return 409 Conflict
4. WHEN a client sends a GET request to `/api/schemas/{schemaId}/versions/{versionId}`, THE Schema_Controller SHALL return the version details
5. THE Response_DTO SHALL include version ID, version identifier, creation timestamp, and deprecated flag

### Requirement 8: Exception Handling

**User Story:** As an API consumer, I want consistent error responses, so that I can handle errors programmatically.

#### Acceptance Criteria

1. WHEN a DocumentSetNotFoundException is thrown, THE Exception_Handler SHALL return 404 Not Found
2. WHEN a DocumentNotFoundException is thrown, THE Exception_Handler SHALL return 404 Not Found
3. WHEN a VersionNotFoundException is thrown, THE Exception_Handler SHALL return 404 Not Found
4. WHEN a SchemaNotFoundException is thrown, THE Exception_Handler SHALL return 404 Not Found
5. WHEN a SchemaVersionNotFoundException is thrown, THE Exception_Handler SHALL return 404 Not Found
6. WHEN a DuplicateDerivativeException is thrown, THE Exception_Handler SHALL return 409 Conflict
7. WHEN a SchemaInUseException is thrown, THE Exception_Handler SHALL return 409 Conflict
8. WHEN an UnsupportedFormatException is thrown, THE Exception_Handler SHALL return 400 Bad Request
9. WHEN an InvalidVersionSequenceException is thrown, THE Exception_Handler SHALL return 400 Bad Request
10. WHEN a ContentHashMismatchException is thrown, THE Exception_Handler SHALL return 400 Bad Request
11. WHEN a validation constraint violation occurs, THE Exception_Handler SHALL return 400 Bad Request with field-level errors
12. THE error Response_DTO SHALL include error code, message, timestamp, and optional details map

### Requirement 9: Request Validation

**User Story:** As an API consumer, I want my requests validated before processing, so that I receive immediate feedback on invalid input.

#### Acceptance Criteria

1. THE Request_DTO for creating document sets SHALL require document type, schema ID, schema version, content (base64), and created by
2. THE Request_DTO for adding documents SHALL require document type, schema ID, schema version, content (base64), and created by
3. THE Request_DTO for adding versions SHALL require content (base64) and created by
4. THE Request_DTO for creating derivatives SHALL require source version number and target format
5. THE Request_DTO for creating schemas SHALL require name and format
6. THE Request_DTO for adding schema versions SHALL require version identifier and definition (base64)
7. WHEN any required field is null or blank, THE Document_Controller SHALL return 400 Bad Request

### Requirement 10: Spring Boot Application Module

**User Story:** As a developer, I want a Spring Boot application module that wires up all domain modules, so that I can run the application as a single deployable unit.

#### Acceptance Criteria

1. THE Application_Module SHALL contain the Spring Boot main class with @SpringBootApplication
2. THE Application_Module SHALL scan and import all domain module configurations
3. THE Application_Module SHALL provide global configuration for CORS
4. THE Application_Module SHALL provide a global fallback exception handler
5. THE Application_Module SHALL be a thin orchestration layer with no business logic
6. THE Application_Module SHALL depend on all domain modules via Maven dependencies

### Requirement 11: Module Configuration

**User Story:** As a developer, I want proper Maven module configuration, so that the project builds correctly with all dependencies.

#### Acceptance Criteria

1. THE Documents_Module pom.xml SHALL include Spring Web dependencies for REST controllers
2. THE Documents_Module pom.xml SHALL include Lombok for reducing boilerplate
3. THE Application_Module pom.xml SHALL include Spring Boot starter web
4. THE Application_Module pom.xml SHALL depend on the documents-domain module
5. THE parent pom.xml SHALL include the application module
6. THE Application_Module SHALL use the spring-boot-maven-plugin for packaging
