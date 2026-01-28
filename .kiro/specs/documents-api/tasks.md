# Implementation Plan: Documents API

## Overview

This plan implements REST API controllers for the Documents domain module and creates a Spring Boot application module. Tasks follow TDD principles - tests are defined before implementation. The implementation builds incrementally, wiring components together at each step.

## Tasks

- [x] 1. Update Maven configuration and add dependencies
  - [x] 1.1 Add minimal dependencies to documents-domain pom.xml
    - Add lombok dependency with provided scope (for DTOs and controllers)
    - Add spring-web dependency (not starter) for @RestController, @RequestMapping annotations only
    - Keep jakarta.validation-api (already present) for validation annotations
    - Note: Spring Boot auto-configuration stays in application module
    - _Requirements: 11.1, 11.2_
  
  - [x] 1.2 Create application module with pom.xml
    - Create application/pom.xml with spring-boot-starter-web (full Spring Boot)
    - Add dependency on documents-domain module
    - Configure spring-boot-maven-plugin for packaging
    - Application module owns Spring Boot configuration and auto-wiring
    - _Requirements: 11.3, 11.4, 11.6_
  
  - [x] 1.3 Update parent pom.xml to include application module
    - Add application module to modules list
    - _Requirements: 11.5_

- [x] 2. Implement Request DTOs with validation
  - [x] 2.1 Create request DTOs for document operations
    - Create CreateDocumentSetRequest record with @NotNull, @NotBlank annotations
    - Create AddDocumentRequest record with validation
    - Create AddVersionRequest record with validation
    - Create CreateDerivativeRequest record with @Min, @NotNull annotations
    - _Requirements: 9.1, 9.2, 9.3, 9.4_
  
  - [x] 2.2 Write unit tests for request DTO validation
    - Test that missing required fields fail validation
    - Test that valid requests pass validation
    - _Requirements: 9.1, 9.2, 9.3, 9.4_
  
  - [x] 2.3 Create request DTOs for schema operations
    - Create CreateSchemaRequest record with validation
    - Create AddSchemaVersionRequest record with validation
    - _Requirements: 9.5, 9.6_
  
  - [x] 2.4 Write unit tests for schema request DTO validation
    - Test that missing required fields fail validation
    - Test that valid requests pass validation
    - _Requirements: 9.5, 9.6_

- [x] 3. Implement Response DTOs
  - [x] 3.1 Create response DTOs for document operations
    - Create DocumentSetResponse record with nested DocumentSummary
    - Create DocumentResponse record
    - Create DocumentVersionResponse record
    - Create DerivativeResponse record
    - Create SchemaRefResponse record
    - _Requirements: 1.5, 2.6, 3.5, 4.5_
  
  - [x] 3.2 Create response DTOs for schema operations
    - Create SchemaResponse record with nested SchemaVersionSummary
    - Create SchemaVersionResponse record
    - _Requirements: 6.5, 7.5_
  
  - [x] 3.3 Create response DTOs for validation and errors
    - Create ValidationResultResponse record with nested error/warning records
    - Create ErrorResponse record with factory methods
    - _Requirements: 5.5, 8.12_

- [x] 4. Checkpoint - Verify DTOs compile and validate correctly
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement Exception Handler
  - [x] 5.1 Create DocumentExceptionHandler with @ControllerAdvice
    - Implement handlers for all domain exceptions
    - Map exceptions to correct HTTP status codes
    - Return ErrorResponse with appropriate details
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9, 8.10, 8.11_
  
  - [x] 5.2 Write unit tests for exception handler
    - Test each exception type maps to correct HTTP status
    - Test error response structure is correct
    - **Property 6: Exception to HTTP Status Mapping**
    - **Validates: Requirements 8.1-8.11**
  
  - [x] 5.3 Write property test for error response structure
    - **Property 7: Error Response Structure**
    - **Validates: Requirements 8.12**

- [x] 6. Implement DocumentSetController
  - [x] 6.1 Create DocumentSetController with POST /api/document-sets endpoint
    - Inject DocumentSetCommandHandler and DocumentSetRepository
    - Implement createDocumentSet method with @Valid request body
    - Map request to CreateDocumentSetCommand
    - Return 201 Created with DocumentSetResponse
    - _Requirements: 1.1, 1.2_
  
  - [x] 6.2 Write unit tests for createDocumentSet
    - Test valid request returns 201 with correct response
    - Test invalid request returns 400
    - **Property 1: Valid Create Requests Return 201 Created**
    - **Validates: Requirements 1.1**
  
  - [x] 6.3 Implement GET /api/document-sets/{id} endpoint
    - Implement getDocumentSet method
    - Return 200 OK with DocumentSetResponse if found
    - Throw DocumentSetNotFoundException if not found
    - _Requirements: 1.3, 1.4_
  
  - [x] 6.4 Write unit tests for getDocumentSet
    - Test found returns 200 with correct response
    - Test not found throws exception (handled by exception handler)
    - **Property 2: Create-Retrieve Round Trip**
    - **Validates: Requirements 1.3**
  
  - [x] 6.5 Implement POST /api/document-sets/{setId}/documents endpoint
    - Implement addDocument method
    - Map request to AddDocumentCommand
    - Return 201 Created with DocumentResponse
    - _Requirements: 2.1, 2.2, 2.3_
  
  - [x] 6.6 Write unit tests for addDocument
    - Test valid request returns 201
    - Test document set not found throws exception
    - **Property 1: Valid Create Requests Return 201 Created**
    - **Validates: Requirements 2.1**
  
  - [x] 6.7 Implement GET /api/document-sets/{setId}/documents/{docId} endpoint
    - Implement getDocument method
    - Return 200 OK with DocumentResponse if found
    - _Requirements: 2.4, 2.5_
  
  - [x] 6.8 Write unit tests for getDocument
    - Test found returns 200 with correct response
    - Test not found throws exception
    - **Property 2: Create-Retrieve Round Trip**
    - **Validates: Requirements 2.4**

- [x] 7. Implement version and derivative endpoints
  - [x] 7.1 Implement POST /api/document-sets/{setId}/documents/{docId}/versions endpoint
    - Implement addVersion method
    - Map request to AddVersionCommand
    - Return 201 Created with DocumentVersionResponse
    - _Requirements: 3.1, 3.2_
  
  - [x] 7.2 Write unit tests for addVersion
    - Test valid request returns 201
    - Test document not found throws exception
    - **Property 1: Valid Create Requests Return 201 Created**
    - **Validates: Requirements 3.1**
  
  - [x] 7.3 Implement GET /api/document-sets/{setId}/documents/{docId}/versions/{versionNumber} endpoint
    - Implement getVersion method
    - Return 200 OK with DocumentVersionResponse if found
    - _Requirements: 3.3, 3.4_
  
  - [x] 7.4 Write unit tests for getVersion
    - Test found returns 200 with correct response
    - Test not found throws exception
    - **Property 2: Create-Retrieve Round Trip**
    - **Validates: Requirements 3.3**
  
  - [x] 7.5 Implement POST /api/document-sets/{setId}/documents/{docId}/derivatives endpoint
    - Implement createDerivative method
    - Map request to CreateDerivativeCommand
    - Return 201 Created with DerivativeResponse
    - _Requirements: 4.1, 4.2, 4.3_
  
  - [x] 7.6 Write unit tests for createDerivative
    - Test valid request returns 201
    - Test duplicate derivative throws exception (409)
    - **Property 5: Duplicate Creation Returns 409 Conflict**
    - **Validates: Requirements 4.3**
  
  - [x] 7.7 Implement GET /api/document-sets/{setId}/documents/{docId}/derivatives endpoint
    - Implement getDerivatives method
    - Return 200 OK with list of DerivativeResponse
    - _Requirements: 4.4_
  
  - [x] 7.8 Write unit tests for getDerivatives
    - Test returns all derivatives for document
    - **Property 8: Derivative Listing Completeness**
    - **Validates: Requirements 4.4**

- [x] 8. Implement validation endpoint
  - [x] 8.1 Implement POST /api/document-sets/{setId}/documents/{docId}/versions/{versionNumber}/validate endpoint
    - Implement validateDocument method
    - Map to ValidateDocumentCommand
    - Return 200 OK with ValidationResultResponse if valid
    - Return 422 Unprocessable Entity if validation fails
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  
  - [x] 8.2 Write unit tests for validateDocument
    - Test valid document returns 200 with valid=true
    - Test invalid document returns 422 with errors
    - Test not found throws exception
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [x] 9. Checkpoint - Verify DocumentSetController works end-to-end
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement SchemaController
  - [x] 10.1 Create SchemaController with POST /api/schemas endpoint
    - Inject SchemaCommandHandler and SchemaRepository
    - Implement createSchema method with @Valid request body
    - Return 201 Created with SchemaResponse
    - _Requirements: 6.1, 6.2_
  
  - [x] 10.2 Write unit tests for createSchema
    - Test valid request returns 201
    - Test invalid request returns 400
    - **Property 1: Valid Create Requests Return 201 Created**
    - **Validates: Requirements 6.1**
  
  - [x] 10.3 Implement GET /api/schemas/{id} endpoint
    - Implement getSchema method
    - Return 200 OK with SchemaResponse if found
    - _Requirements: 6.3, 6.4_
  
  - [x] 10.4 Write unit tests for getSchema
    - Test found returns 200 with correct response
    - Test not found throws exception
    - **Property 2: Create-Retrieve Round Trip**
    - **Validates: Requirements 6.3**
  
  - [x] 10.5 Implement POST /api/schemas/{schemaId}/versions endpoint
    - Implement addVersion method
    - Map request to AddSchemaVersionCommand
    - Return 201 Created with SchemaVersionResponse
    - _Requirements: 7.1, 7.2, 7.3_
  
  - [x] 10.6 Write unit tests for addVersion
    - Test valid request returns 201
    - Test schema not found throws exception
    - Test duplicate version throws exception (409)
    - **Property 5: Duplicate Creation Returns 409 Conflict**
    - **Validates: Requirements 7.3**
  
  - [x] 10.7 Implement GET /api/schemas/{schemaId}/versions/{versionId} endpoint
    - Implement getVersion method
    - Return 200 OK with SchemaVersionResponse if found
    - _Requirements: 7.4_
  
  - [x] 10.8 Write unit tests for getVersion
    - Test found returns 200 with correct response
    - Test not found throws exception
    - **Property 2: Create-Retrieve Round Trip**
    - **Validates: Requirements 7.4**

- [x] 11. Checkpoint - Verify SchemaController works end-to-end
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Implement Application Module
  - [x] 12.1 Create Application.java with @SpringBootApplication
    - Configure component scanning for domain modules
    - _Requirements: 10.1, 10.2_
  
  - [x] 12.2 Create WebConfig for CORS configuration
    - Implement WebMvcConfigurer
    - Configure CORS for /api/** endpoints
    - _Requirements: 10.3_
  
  - [x] 12.3 Create GlobalExceptionHandler as fallback
    - Handle unexpected exceptions with 500 Internal Server Error
    - Use @Order(Ordered.LOWEST_PRECEDENCE) to run after domain handlers
    - _Requirements: 10.4_
  
  - [x] 12.4 Create application.yml with default configuration
    - Configure server port
    - Configure documents module properties
    - _Requirements: 10.5_

- [x] 13. Write property-based tests for API correctness
  - [x] 13.1 Write property test for response structure completeness
    - Generate random valid requests
    - Verify all required fields present in responses
    - **Property 3: Response Structure Completeness**
    - **Validates: Requirements 1.5, 2.6, 3.5, 4.5, 5.5, 6.5, 7.5**
  
  - [x] 13.2 Write property test for missing required fields
    - Generate requests with various fields nulled
    - Verify 400 Bad Request returned
    - **Property 4: Missing Required Fields Return 400**
    - **Validates: Requirements 1.2, 6.2, 9.1-9.7**

- [ ] 14. Final checkpoint - Full integration verification
  - Ensure all tests pass, ask the user if questions arise.
  - Verify application starts successfully
  - Verify all endpoints accessible

## Notes

- All tasks are required for comprehensive test coverage
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- TDD approach: test tasks precede or accompany implementation tasks
- Domain module uses minimal Spring dependencies (spring-web only for annotations)
- Spring Boot auto-configuration and starters remain in application module
