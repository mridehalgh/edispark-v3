# Design Document

## Overview

This design describes a Java library generated from UBL 2.1 JSON schemas using the `jsonschema2pojo` Maven plugin. The library provides type-safe Java classes for all 65+ UBL document types with Jackson-based JSON serialization/deserialization. The approach leverages an established, well-maintained code generation tool rather than building custom generation logic.

## Architecture

The solution follows a build-time code generation pattern:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Build Process                             │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐    ┌───────────────────┐    ┌──────────────┐ │
│  │ UBL JSON     │───▶│ jsonschema2pojo   │───▶│ Generated    │ │
│  │ Schemas      │    │ Maven Plugin      │    │ Java Classes │ │
│  └──────────────┘    └───────────────────┘    └──────────────┘ │
│        │                      │                       │         │
│        ▼                      ▼                       ▼         │
│  ubl-source/          Plugin Config            target/          │
│  json-schema/         in pom.xml              generated-sources/│
└─────────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

1. **Use jsonschema2pojo**: Mature, well-documented tool with Maven integration
2. **Jackson for JSON**: Industry standard, excellent performance, annotation support
3. **JSR-380 Validation**: Bean Validation annotations for required fields
4. **Immutable-friendly**: Generate with builders for fluent construction

## Components and Interfaces

### Project Structure

```
ubl-java-library/
├── pom.xml                              # Maven build with jsonschema2pojo plugin
├── ubl-source/
│   └── json-schema/
│       ├── common/                      # Shared component schemas
│       │   ├── UBL-CommonBasicComponents-2.1.json
│       │   ├── UBL-CommonAggregateComponents-2.1.json
│       │   └── ...
│       └── maindoc/                     # Document type schemas
│           ├── UBL-Invoice-2.1.json
│           ├── UBL-Order-2.1.json
│           └── ...
├── src/
│   └── main/
│       └── java/
│           └── com/example/ubl/
│               └── util/                # Hand-written utilities (if needed)
│                   └── UblJsonMapper.java
└── target/
    └── generated-sources/
        └── java/
            └── oasis/
                └── names/
                    └── specification/
                        └── ubl/
                            └── schema/
                                └── xsd/
                                    ├── invoice_2/
                                    │   └── Invoice.java
                                    ├── order_2/
                                    │   └── Order.java
                                    └── commonbasiccomponents_2/
                                        └── *.java
```

### Maven Plugin Configuration

```xml
<plugin>
    <groupId>org.jsonschema2pojo</groupId>
    <artifactId>jsonschema2pojo-maven-plugin</artifactId>
    <version>1.2.2</version>
    <configuration>
        <sourceDirectory>${basedir}/ubl-source/json-schema</sourceDirectory>
        <targetPackage>oasis.names.specification.ubl.schema.xsd</targetPackage>
        <outputDirectory>${project.build.directory}/generated-sources/java</outputDirectory>
        
        <!-- JSON Schema settings -->
        <sourceType>jsonschema</sourceType>
        <annotationStyle>jackson2</annotationStyle>
        
        <!-- Generation options -->
        <generateBuilders>true</generateBuilders>
        <includeConstructors>true</includeConstructors>
        <includeJsr303Annotations>true</includeJsr303Annotations>
        <useOptionalForGetters>false</useOptionalForGetters>
        <includeAdditionalProperties>false</includeAdditionalProperties>
        
        <!-- Type mappings -->
        <useBigDecimals>true</useBigDecimals>
        <dateType>java.time.LocalDate</dateType>
        <timeType>java.time.LocalTime</timeType>
        <dateTimeType>java.time.OffsetDateTime</dateTimeType>
        
        <!-- Documentation -->
        <includeJsr305Annotations>false</includeJsr305Annotations>
        <includeGeneratedAnnotation>true</includeGeneratedAnnotation>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Generated Class Structure

For a UBL Invoice, the generated class will look like:

```java
package oasis.names.specification.ubl.schema.xsd.invoice_2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * A document used to request payment.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Invoice {

    @JsonProperty("ID")
    @NotNull
    private List<Identifier> id;

    @JsonProperty("IssueDate")
    @NotNull
    private List<Date> issueDate;

    @JsonProperty("AccountingSupplierParty")
    @NotNull
    @Valid
    private List<SupplierParty> accountingSupplierParty;

    @JsonProperty("InvoiceLine")
    @NotNull
    @Valid
    private List<InvoiceLine> invoiceLine;

    // Getters, setters, builders...
    
    public static class Builder {
        // Builder pattern for fluent construction
    }
}
```

### UBL JSON Value Pattern Handling

UBL JSON uses a specific pattern where primitive values are wrapped in objects with a `_` property:

```json
{"ID": [{"_": "INV-001", "schemeID": "GLN"}]}
```

The jsonschema2pojo plugin will generate classes that match this structure. For basic components:

```java
/**
 * Identifier type with optional scheme attributes
 */
public class Identifier {
    @JsonProperty("_")
    private String value;
    
    @JsonProperty("schemeID")
    private String schemeId;
    
    @JsonProperty("schemeAgencyID")
    private String schemeAgencyId;
    
    // getters, setters, builders
}
```

### Custom ObjectMapper Configuration

A utility class provides pre-configured Jackson ObjectMapper:

```java
package com.example.ubl.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class UblJsonMapper {
    
    private static final ObjectMapper MAPPER = createMapper();
    
    private UblJsonMapper() {}
    
    public static ObjectMapper getInstance() {
        return MAPPER;
    }
    
    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
}
```

## Data Models

### Package Organization

Generated packages follow the UBL namespace structure:

| Schema Directory | Generated Package |
|-----------------|-------------------|
| `maindoc/UBL-Invoice-2.1.json` | `oasis.names.specification.ubl.schema.xsd.invoice_2` |
| `maindoc/UBL-Order-2.1.json` | `oasis.names.specification.ubl.schema.xsd.order_2` |
| `common/UBL-CommonBasicComponents-2.1.json` | `oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_2` |
| `common/UBL-CommonAggregateComponents-2.1.json` | `oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_2` |

### Type Mappings

| JSON Schema Type | Java Type |
|-----------------|-----------|
| `string` | `String` |
| `number` | `BigDecimal` |
| `integer` | `Integer` |
| `boolean` | `Boolean` |
| `DateType` | `LocalDate` |
| `TimeType` | `LocalTime` |
| `array` | `List<T>` |
| `object` | Generated class |

### Key Generated Types

**Document Types** (65+ classes):
- `Invoice`, `CreditNote`, `DebitNote`
- `Order`, `OrderResponse`, `OrderCancellation`
- `DespatchAdvice`, `ReceiptAdvice`
- `Quotation`, `RequestForQuotation`
- `Waybill`, `BillOfLading`
- etc.

**Common Aggregate Components** (~200 classes):
- `Party`, `Address`, `Contact`
- `InvoiceLine`, `OrderLine`
- `TaxTotal`, `TaxSubtotal`, `TaxCategory`
- `MonetaryTotal`, `AllowanceCharge`
- `Delivery`, `DeliveryTerms`
- etc.

**Common Basic Components** (~500 types):
- `Identifier`, `Amount`, `Quantity`
- `Date`, `Time`, `Code`, `Text`
- `Measure`, `Percent`, `Rate`
- etc.



## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

Based on the prework analysis, the following correctness properties have been identified. These focus on the core behaviors that must hold across all inputs.

### Property 1: JSON Document Round-Trip Consistency

*For any* valid UBL JSON document from the sample files, deserializing to a Java object and then serializing back to JSON SHALL produce semantically equivalent JSON (same data, potentially different formatting).

**Validates: Requirements 5.1, 4.1, 3.1, 3.2**

This is the primary correctness property for the library. It ensures that:
- Deserialization correctly parses all UBL JSON structures
- Serialization correctly produces UBL JSON format
- No data is lost in the conversion process
- The `_` property pattern is handled correctly
- Array wrappers are preserved

### Property 2: Java Object Round-Trip Consistency

*For any* programmatically constructed UBL Java object with valid field values, serializing to JSON and then deserializing back to a Java object SHALL produce an equivalent object (equals() returns true).

**Validates: Requirements 5.2, 3.1, 4.1**

This property validates that:
- Objects can be constructed programmatically
- Serialization produces valid JSON
- Deserialization reconstructs equivalent objects
- All attributes (schemeID, currencyID, etc.) are preserved

### Property 3: Generation Completeness

*For any* JSON schema file in the `ubl-source/json-schema/maindoc/` directory, the build process SHALL generate a corresponding Java class that can be instantiated.

**Validates: Requirements 2.1, 1.1, 6.1**

This property ensures that:
- All 65+ UBL document types have generated classes
- The generation process doesn't silently skip schemas
- Generated classes are valid Java (compile successfully)

### Property 4: Required Field Validation Annotations

*For any* field marked as required in a UBL JSON schema, the corresponding Java field SHALL have a `@NotNull` validation annotation.

**Validates: Requirements 2.7, 7.3**

This property ensures that:
- Bean Validation can enforce required fields
- The mapping from schema `required` to Java annotations is correct

## Error Handling

### Build-Time Errors

| Error Condition | Handling |
|----------------|----------|
| Schema file not found | Maven build fails with file path in error message |
| Invalid JSON in schema | jsonschema2pojo reports parse error with location |
| Unresolved `$ref` | Build fails with reference path in error |
| Java compilation error | Standard Maven compiler error reporting |

### Runtime Errors

| Error Condition | Exception Type | Handling |
|----------------|---------------|----------|
| Malformed JSON input | `JsonParseException` | Includes line/column of error |
| Unknown property in JSON | Configurable: ignore or fail | Default: ignore unknown properties |
| Type mismatch | `JsonMappingException` | Includes field name and expected type |
| Validation failure | `ConstraintViolationException` | Lists all violated constraints |

### Error Response Pattern

```java
try {
    Invoice invoice = UblJsonMapper.getInstance()
        .readValue(jsonString, InvoiceDocument.class)
        .getInvoice().get(0);
} catch (JsonParseException e) {
    // Malformed JSON
    log.error("Invalid JSON at line {}, column {}", 
        e.getLocation().getLineNr(), 
        e.getLocation().getColumnNr());
} catch (JsonMappingException e) {
    // Type/structure mismatch
    log.error("Mapping error for field: {}", e.getPath());
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
- Tag format: `Feature: ubl-java-library, Property N: [property description]`

### Test Categories

#### Unit Tests

1. **Sample Document Tests**
   - Parse each sample JSON file from `ubl-source/json/`
   - Verify key fields are correctly populated
   - Test specific UBL patterns (array wrappers, `_` property)

2. **Error Handling Tests**
   - Malformed JSON input
   - Missing required fields
   - Type mismatches

3. **Edge Case Tests**
   - Empty arrays
   - Null optional fields
   - Special characters in text fields
   - Large numeric values

#### Property-Based Tests

1. **Round-Trip Property Tests**
   - Generate random valid UBL objects
   - Verify JSON round-trip consistency
   - Verify Java object round-trip consistency

2. **Generation Completeness Tests**
   - Enumerate all schema files
   - Verify corresponding class exists and is instantiable

3. **Validation Annotation Tests**
   - Reflect on generated classes
   - Verify required fields have @NotNull

### Test File Organization

```
src/test/java/
└── oasis/names/specification/ubl/
    ├── RoundTripPropertyTest.java      # Property tests for round-trip
    ├── GenerationCompletenessTest.java # Property test for generation
    ├── ValidationAnnotationTest.java   # Property test for annotations
    ├── SampleDocumentTest.java         # Unit tests with sample files
    └── ErrorHandlingTest.java          # Unit tests for error cases
```

### Sample Property Test Structure

```java
@Property(tries = 100)
@Label("Feature: ubl-java-library, Property 1: JSON round-trip consistency")
void jsonRoundTripPreservesData(@ForAll("validInvoiceJson") String originalJson) {
    // Parse JSON to Java
    InvoiceDocument doc = mapper.readValue(originalJson, InvoiceDocument.class);
    
    // Serialize back to JSON
    String serializedJson = mapper.writeValueAsString(doc);
    
    // Parse again
    InvoiceDocument reparsed = mapper.readValue(serializedJson, InvoiceDocument.class);
    
    // Verify equivalence
    assertThat(reparsed).isEqualTo(doc);
}
```
