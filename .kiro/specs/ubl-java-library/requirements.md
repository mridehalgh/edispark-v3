# Requirements Document

## Introduction

This document specifies the requirements for generating a Java library from UBL (Universal Business Language) 2.1 JSON schemas. The library will provide type-safe Java classes for all UBL document types (Invoice, Order, CreditNote, etc.) with JSON serialization/deserialization capabilities. The goal is to leverage existing code generation tools to transform the JSON schemas in `ubl-source/json-schema` into a usable Java library.

## Glossary

- **UBL**: Universal Business Language - an OASIS standard for business documents
- **JSON_Schema**: JSON Schema draft-07 format defining the structure of UBL documents
- **Generator**: A tool or library that converts JSON schemas into Java source code
- **Document_Type**: A top-level UBL business document (e.g., Invoice, Order, CreditNote)
- **Common_Component**: Shared data types used across multiple document types (BasicComponents, AggregateComponents)
- **Value_Object**: An immutable Java object representing a UBL data element
- **Serializer**: Component that converts Java objects to JSON format
- **Deserializer**: Component that converts JSON format to Java objects

## Requirements

### Requirement 1: Schema Discovery and Loading

**User Story:** As a developer, I want the build process to discover and load all UBL JSON schemas, so that all document types are available for code generation.

#### Acceptance Criteria

1. WHEN the build process starts, THE Generator SHALL locate all JSON schema files in `ubl-source/json-schema/maindoc/` directory
2. WHEN the build process starts, THE Generator SHALL locate all JSON schema files in `ubl-source/json-schema/common/` directory
3. WHEN a schema file is found, THE Generator SHALL parse it as valid JSON Schema draft-07
4. IF a schema file cannot be parsed, THEN THE Generator SHALL report a descriptive error including the file path and parse error

### Requirement 2: Java Class Generation

**User Story:** As a developer, I want Java classes generated from UBL schemas, so that I can work with UBL documents in a type-safe manner.

#### Acceptance Criteria

1. WHEN a document schema is processed, THE Generator SHALL create a Java class for each document type (Invoice, Order, CreditNote, etc.)
2. WHEN a common component schema is processed, THE Generator SHALL create Java classes for all aggregate and basic component types
3. THE Generator SHALL generate Java classes that follow Java naming conventions (PascalCase for classes, camelCase for fields)
4. THE Generator SHALL generate appropriate Java types for JSON schema types (String for string, BigDecimal for number, Boolean for boolean, LocalDate for date, LocalTime for time)
5. WHEN a schema property has `maxItems: 1`, THE Generator SHALL generate a single-valued field (not a List)
6. WHEN a schema property has no `maxItems` or `maxItems > 1`, THE Generator SHALL generate a List field
7. WHEN a schema property is in the `required` array, THE Generator SHALL annotate the field appropriately for validation

### Requirement 3: JSON Serialization Support

**User Story:** As a developer, I want to serialize Java UBL objects to JSON, so that I can produce valid UBL JSON documents.

#### Acceptance Criteria

1. THE Serializer SHALL convert any UBL Java object to valid JSON matching the UBL JSON format
2. WHEN serializing, THE Serializer SHALL produce JSON that conforms to the original UBL JSON schema
3. THE Serializer SHALL handle the UBL JSON array wrapper pattern (where single values are wrapped in arrays)
4. THE Serializer SHALL correctly serialize the `_` property pattern used for primitive values with attributes
5. WHEN a field is null or empty, THE Serializer SHALL omit it from the output (unless required)

### Requirement 4: JSON Deserialization Support

**User Story:** As a developer, I want to deserialize UBL JSON documents to Java objects, so that I can process incoming UBL documents.

#### Acceptance Criteria

1. THE Deserializer SHALL convert valid UBL JSON documents to corresponding Java objects
2. WHEN deserializing, THE Deserializer SHALL correctly handle the UBL JSON array wrapper pattern
3. THE Deserializer SHALL correctly parse the `_` property pattern for primitive values with attributes
4. IF the JSON is malformed, THEN THE Deserializer SHALL throw a descriptive exception
5. IF required fields are missing, THEN THE Deserializer SHALL throw a validation exception

### Requirement 5: Round-Trip Consistency

**User Story:** As a developer, I want serialization and deserialization to be consistent, so that I don't lose data when processing UBL documents.

#### Acceptance Criteria

1. FOR ALL valid UBL JSON documents, deserializing then serializing SHALL produce semantically equivalent JSON
2. FOR ALL valid UBL Java objects, serializing then deserializing SHALL produce an equivalent Java object
3. THE Serializer and Deserializer SHALL preserve all data attributes (schemeID, listID, currencyID, etc.)

### Requirement 6: Build Integration

**User Story:** As a developer, I want the code generation integrated into the Maven build, so that classes are generated automatically.

#### Acceptance Criteria

1. WHEN running `mvn compile`, THE Generator SHALL produce Java source files before compilation
2. THE Generator SHALL place generated sources in `target/generated-sources/` directory
3. THE Generator SHALL only regenerate sources when schema files have changed
4. IF generation fails, THEN THE Generator SHALL fail the build with a clear error message

### Requirement 7: Library Packaging

**User Story:** As a developer, I want the generated library packaged as a Maven artifact, so that I can use it as a dependency in other projects.

#### Acceptance Criteria

1. THE Build SHALL produce a JAR file containing all generated UBL classes
2. THE Build SHALL include Jackson annotations for JSON processing
3. THE Build SHALL include Bean Validation annotations for required field validation
4. THE Library SHALL have minimal runtime dependencies (Jackson, validation-api)

### Requirement 8: Documentation

**User Story:** As a developer, I want generated classes to have documentation, so that I understand what each field represents.

#### Acceptance Criteria

1. WHEN a schema element has a `description`, THE Generator SHALL include it as a Javadoc comment
2. WHEN a schema element has a `title`, THE Generator SHALL include it in the Javadoc
3. THE Generator SHALL document the UBL version (2.1) in package-level documentation
