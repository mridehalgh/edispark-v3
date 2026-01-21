# Implementation Plan: UBL Java Library

## Overview

This implementation plan creates a Java library from UBL 2.1 JSON schemas using the jsonschema2pojo Maven plugin. The approach focuses on configuring the build correctly, then validating the generated code through property-based and unit tests.

## Tasks

- [x] 1. Set up Maven project structure
  - [x] 1.1 Create parent pom.xml with Java 25 and Spring Boot parent
    - Configure Maven wrapper
    - Set Java version to 25
    - Add dependency management for Jackson and validation
    - _Requirements: 6.1, 7.4_
  
  - [x] 1.2 Configure jsonschema2pojo Maven plugin
    - Add plugin with version 1.2.2
    - Configure sourceDirectory to point to ubl-source/json-schema
    - Set targetPackage to oasis.names.specification.ubl.schema.xsd
    - Enable Jackson2 annotations, builders, JSR-303 annotations
    - Configure date/time types to use java.time classes
    - Configure BigDecimal for numbers
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 6.1, 6.2_

  - [x] 1.3 Add runtime dependencies
    - Add Jackson databind and datatype-jsr310
    - Add Jakarta validation-api
    - Add test dependencies (JUnit 5, jqwik, AssertJ)
    - _Requirements: 7.2, 7.3, 7.4_

- [x] 2. Verify code generation
  - [x] 2.1 Run initial Maven build to generate sources
    - Execute `mvn generate-sources`
    - Verify classes are generated in target/generated-sources/java
    - Check for any generation errors or warnings
    - _Requirements: 1.1, 1.2, 6.1, 6.2_

  - [x] 2.2 Verify generated class structure
    - Confirm Invoice, Order, CreditNote classes exist
    - Confirm common component classes exist
    - Check that classes have Jackson annotations
    - Check that required fields have @NotNull annotations
    - _Requirements: 2.1, 2.2, 2.7, 7.2, 7.3_

- [x] 3. Create utility classes
  - [x] 3.1 Implement UblJsonMapper utility
    - Create pre-configured ObjectMapper singleton
    - Register JavaTimeModule for date/time handling
    - Configure serialization inclusion (NON_NULL)
    - Disable WRITE_DATES_AS_TIMESTAMPS
    - _Requirements: 3.1, 3.3, 3.4, 4.1_

- [x] 4. Checkpoint - Verify basic generation works
  - Ensure `mvn compile` succeeds
  - Verify generated sources compile without errors
  - Ask the user if questions arise

- [x] 5. Implement sample document tests
  - [x] 5.1 Create SampleDocumentTest for Invoice parsing
    - Parse UBL-Invoice-2.1-Example.json
    - Verify key fields (ID, IssueDate, AccountingSupplierParty)
    - Verify nested structures (InvoiceLine, TaxTotal)
    - _Requirements: 4.1, 4.2, 4.3_

  - [x] 5.2 Create tests for other document types
    - Test Order, CreditNote, Quotation parsing
    - Verify document-specific fields
    - _Requirements: 4.1_

  - [x] 5.3 Write property test for JSON round-trip
    - **Property 1: JSON Document Round-Trip Consistency**
    - **Validates: Requirements 5.1, 4.1, 3.1, 3.2**
    - Use sample JSON files as test inputs
    - Parse to Java, serialize back, parse again
    - Verify equivalence

- [x] 6. Implement generation completeness tests
  - [x] 6.1 Write property test for generation completeness
    - **Property 3: Generation Completeness**
    - **Validates: Requirements 2.1, 1.1, 6.1**
    - Enumerate all schema files in maindoc/
    - Verify corresponding Java class exists
    - Verify class can be instantiated

  - [x] 6.2 Write property test for validation annotations
    - **Property 4: Required Field Validation Annotations**
    - **Validates: Requirements 2.7, 7.3**
    - Reflect on generated classes
    - For each required field in schema, verify @NotNull annotation

- [x] 7. Implement object construction tests
  - [x] 7.1 Create InvoiceBuilderTest
    - Programmatically construct Invoice using builders
    - Verify all fields are set correctly
    - Test nested object construction
    - _Requirements: 2.1, 3.1_

  - [x] 7.2 Write property test for Java object round-trip
    - **Property 2: Java Object Round-Trip Consistency**
    - **Validates: Requirements 5.2, 3.1, 4.1**
    - Generate random valid Invoice objects
    - Serialize to JSON, deserialize back
    - Verify equals() returns true

- [x] 8. Implement error handling tests
  - [x] 8.1 Create ErrorHandlingTest
    - Test malformed JSON input throws JsonParseException
    - Test type mismatch throws JsonMappingException
    - Test missing required fields with validation
    - _Requirements: 4.4, 4.5_

- [x] 9. Final checkpoint - Ensure all tests pass
  - Run `mvn verify` to execute all tests
  - Ensure all property tests pass with 100+ iterations
  - Verify JAR is produced with all classes
  - Ask the user if questions arise

## Notes

- All tasks are required for comprehensive coverage
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- The jsonschema2pojo plugin handles most of the heavy lifting; our code is primarily configuration and testing
