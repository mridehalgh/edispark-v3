# Design: Paginated Document Sets Listing

## 1. Overview

This design implements server-side pagination for the document sets listing endpoint using DynamoDB's native pagination mechanism with Query operations. The implementation abstracts DynamoDB's `LastEvaluatedKey` into opaque Base64-encoded tokens that clients treat as continuation strings.

**Key architectural decision**: Pagination concerns are extracted into a new `support/common` module to ensure consistent pagination patterns across all bounded contexts. This prevents each domain from implementing its own pagination style and promotes reusability.

## 2. Architecture

### 2.1 Module Structure

```
support/
└── common/                          # NEW: Shared cross-cutting concerns
    └── src/main/java/com/example/common/
        ├── pagination/
        │   ├── PaginatedResult.java      # Generic paginated result
        │   └── Page.java                 # Page request parameters
        └── api/
            └── PaginatedResponse.java    # Generic API response DTO

domains/
└── documents/
    └── src/main/java/com/example/documents/
        ├── api/
        │   └── dto/
        │       └── PaginatedDocumentSetResponse.java  # Uses PaginatedResponse<T>
        ├── infrastructure/
        │   └── persistence/
        │       └── PaginationTokenCodec.java  # DynamoDB-specific encoding
        └── ...
```

### 2.2 Layered Design

```
┌─────────────────────────────────────────┐
│  REST Controller Layer                  │
│  - Accepts limit & nextToken params     │
│  - Returns PaginatedResponse<T>         │  ← From support/common
│  - Validates input parameters           │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  Application Layer                      │
│  - Query handler for pagination         │
│  - Token encoding/decoding              │
│  - Business validation                  │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  Domain Repository Interface            │
│  - findAll(Page)                        │
│  - Returns PaginatedResult<T>           │  ← From support/common
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  Infrastructure Layer                   │
│  - DynamoDB Query on GSI1               │
│  - Handles LastEvaluatedKey             │
│  - NO SCAN operations                   │
└─────────────────────────────────────────┘
```

### 2.3 Data Flow

1. Client sends `GET /api/document-sets?limit=20&nextToken=xyz`
2. Controller validates parameters and decodes nextToken
3. Application layer calls repository with decoded key
4. Repository executes DynamoDB Query on GSI1
5. Repository returns items + LastEvaluatedKey
6. Application layer encodes LastEvaluatedKey to nextToken
7. Controller returns paginated response with encoded token

## 3. Component Design

### 3.0 Support Module (NEW)

#### 3.0.1 Module POM

Create `support/common/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example</groupId>
        <artifactId>ubl-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>common</artifactId>
    <name>Common Support Library</name>
    <description>Shared cross-cutting concerns for all bounded contexts</description>

    <dependencies>
        <!-- Lombok for boilerplate reduction -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Test dependencies -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>net.jqwik</groupId>
            <artifactId>jqwik</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Update parent `pom.xml` to include the new module:

```xml
<modules>
    <module>support/common</module>
    <module>ubl-model</module>
    <module>domains/documents</module>
    <module>application</module>
</modules>
```

#### 3.0.2 Page Request Object

```java
package com.example.common.pagination;

import java.util.Optional;

/**
 * Represents pagination parameters for a query.
 * 
 * <p>This is a framework-agnostic representation of page request parameters
 * that can be used across all bounded contexts.</p>
 * 
 * @param limit maximum number of items to return (must be positive)
 * @param continuationToken optional token from previous page
 */
public record Page(
    int limit,
    Optional<String> continuationToken
) {
    public Page {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive");
        }
    }
    
    /**
     * Creates a page request with default limit of 20.
     */
    public static Page first() {
        return new Page(20, Optional.empty());
    }
    
    /**
     * Creates a page request with specified limit.
     */
    public static Page first(int limit) {
        return new Page(limit, Optional.empty());
    }
    
    /**
     * Creates a page request for the next page.
     */
    public static Page next(int limit, String continuationToken) {
        return new Page(limit, Optional.of(continuationToken));
    }
    
    public boolean isFirstPage() {
        return continuationToken.isEmpty();
    }
}
```

#### 3.0.3 PaginatedResult Value Object

```java
package com.example.common.pagination;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Represents a paginated result set with continuation token.
 * 
 * <p>This is a domain-layer abstraction that is independent of any
 * persistence technology or API framework.</p>
 * 
 * @param <T> the type of items in the result
 * @param items the list of items in this page (never null)
 * @param continuationToken optional opaque token for fetching next page
 */
public record PaginatedResult<T>(
    List<T> items,
    Optional<String> continuationToken
) {
    public PaginatedResult {
        if (items == null) {
            throw new IllegalArgumentException("Items cannot be null");
        }
    }
    
    /**
     * Returns true if there are more pages available.
     */
    public boolean hasMore() {
        return continuationToken.isPresent();
    }
    
    /**
     * Returns the number of items in this page.
     */
    public int size() {
        return items.size();
    }
    
    /**
     * Returns true if this page is empty.
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }
    
    /**
     * Maps items to a different type while preserving pagination metadata.
     */
    public <R> PaginatedResult<R> map(Function<T, R> mapper) {
        List<R> mappedItems = items.stream()
            .map(mapper)
            .toList();
        return new PaginatedResult<>(mappedItems, continuationToken);
    }
    
    /**
     * Creates a paginated result with items and continuation token.
     */
    public static <T> PaginatedResult<T> of(List<T> items, String continuationToken) {
        return new PaginatedResult<>(items, Optional.ofNullable(continuationToken));
    }
    
    /**
     * Creates a paginated result for the last page (no more results).
     */
    public static <T> PaginatedResult<T> lastPage(List<T> items) {
        return new PaginatedResult<>(items, Optional.empty());
    }
    
    /**
     * Creates an empty result (no items, no more pages).
     */
    public static <T> PaginatedResult<T> empty() {
        return new PaginatedResult<>(List.of(), Optional.empty());
    }
}
```

#### 3.0.4 PaginatedResponse API DTO

```java
package com.example.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Generic paginated response for REST APIs.
 * 
 * <p>This provides a consistent pagination structure across all API endpoints.
 * The nextToken field is omitted from JSON when null (last page).</p>
 * 
 * @param <T> the type of items in the response
 * @param items the list of items in this page
 * @param nextToken optional opaque token for fetching next page (null if last page)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaginatedResponse<T>(
    List<T> items,
    String nextToken
) {
    public PaginatedResponse {
        if (items == null) {
            throw new IllegalArgumentException("Items cannot be null");
        }
    }
    
    /**
     * Creates a paginated response from domain PaginatedResult.
     */
    public static <T> PaginatedResponse<T> from(
            com.example.common.pagination.PaginatedResult<T> result) {
        return new PaginatedResponse<>(
            result.items(),
            result.continuationToken().orElse(null)
        );
    }
}
```

### 3.1 Domain Layer

### 3.1 Domain Layer

#### 3.1.1 Repository Interface Update

```java
package com.example.documents.domain.repository;

import com.example.common.pagination.Page;
import com.example.common.pagination.PaginatedResult;
import com.example.documents.domain.model.DocumentSet;
import java.util.Optional;

public interface DocumentSetRepository {
    // Existing methods...
    
    /**
     * Finds document sets with pagination support.
     * 
     * @param page pagination parameters (limit and continuation token)
     * @return paginated result with items and next token
     */
    PaginatedResult<DocumentSet> findAll(Page page);
}
```

Note: The domain module must add a dependency on `support/common`:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>common</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 3.2 Application Layer

#### 3.2.1 Query Command

```java
package com.example.documents.application.query;

import com.example.common.pagination.Page;

/**
 * Query for retrieving paginated document sets.
 * 
 * @param page pagination parameters
 */
public record ListDocumentSetsQuery(Page page) {
    
    /**
     * Creates a query with validated parameters.
     * 
     * @param limit number of items per page (1-100, null defaults to 20)
     * @param nextToken optional continuation token from previous page
     * @return validated query
     * @throws IllegalArgumentException if limit is out of range
     */
    public static ListDocumentSetsQuery of(Integer limit, String nextToken) {
        int pageLimit = (limit != null) ? limit : 20;
        
        if (pageLimit < 1 || pageLimit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
        
        Page page = (nextToken != null) 
            ? Page.next(pageLimit, nextToken)
            : Page.first(pageLimit);
            
        return new ListDocumentSetsQuery(page);
    }
}
```

#### 3.2.2 Query Handler

```java
package com.example.documents.application.handler;

import com.example.common.pagination.PaginatedResult;
import com.example.documents.application.query.ListDocumentSetsQuery;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.repository.DocumentSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Handles queries for document set listing.
 */
@Service
@RequiredArgsConstructor
public class DocumentSetQueryHandler {
    
    private final DocumentSetRepository repository;
    
    /**
     * Executes paginated document set listing query.
     * 
     * @param query the pagination query
     * @return paginated result with document sets
     */
    public PaginatedResult<DocumentSet> handle(ListDocumentSetsQuery query) {
        return repository.findAll(query.page());
    }
}
```

#### 3.2.3 Pagination Token Codec

```java
package com.example.documents.infrastructure.persistence;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Encodes and decodes DynamoDB pagination tokens.
 * 
 * <p>Tokens are Base64-encoded JSON representations of DynamoDB's LastEvaluatedKey.
 * This abstraction hides DynamoDB implementation details from API clients.</p>
 */
public class PaginationTokenCodec {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    /**
     * Encodes a DynamoDB LastEvaluatedKey into an opaque token string.
     * 
     * @param lastEvaluatedKey the DynamoDB key map
     * @return Base64-encoded token, or null if key is null/empty
     */
    public static String encode(Map<String, AttributeValue> lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }
        
        try {
            // Convert AttributeValue map to simple string map for JSON serialization
            Map<String, String> simpleMap = new HashMap<>();
            lastEvaluatedKey.forEach((key, value) -> {
                if (value.s() != null) {
                    simpleMap.put(key, value.s());
                }
            });
            
            String json = MAPPER.writeValueAsString(simpleMap);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode pagination token", e);
        }
    }
    
    /**
     * Decodes an opaque token string into a DynamoDB ExclusiveStartKey.
     * 
     * @param token the Base64-encoded token
     * @return DynamoDB key map
     * @throws InvalidPaginationTokenException if token is malformed
     */
    public static Map<String, AttributeValue> decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String json = new String(decoded);
            
            @SuppressWarnings("unchecked")
            Map<String, String> simpleMap = MAPPER.readValue(json, Map.class);
            
            // Convert back to AttributeValue map
            Map<String, AttributeValue> attributeMap = new HashMap<>();
            simpleMap.forEach((key, value) -> 
                attributeMap.put(key, AttributeValue.builder().s(value).build())
            );
            
            return attributeMap;
        } catch (Exception e) {
            throw new InvalidPaginationTokenException("Invalid or corrupted pagination token", e);
        }
    }
}
```

#### 3.2.4 Exception Classes

```java
package com.example.documents.application.handler;

/**
 * Thrown when a pagination token cannot be decoded or is invalid.
 */
public class InvalidPaginationTokenException extends RuntimeException {
    public InvalidPaginationTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### 3.3 Infrastructure Layer

#### 3.3.1 DynamoDB Repository Implementation

Update `DynamoDbDocumentSetRepository` to add paginated findAll:

```java
@Override
public PaginatedResult<DocumentSet> findAll(Page page) {
    // Decode continuation token if present
    Map<String, AttributeValue> exclusiveStartKey = page.continuationToken()
        .map(PaginationTokenCodec::decode)
        .orElse(null);
    
    // Query GSI1 to get document sets for this tenant
    String gsi1Pk = gsi1PkForTenantDocumentSets(tenantId);
    
    QueryRequest.Builder requestBuilder = QueryRequest.builder()
        .tableName(tableName)
        .indexName(GSI1_NAME)
        .keyConditionExpression("#gsi1pk = :gsi1pk")
        .expressionAttributeNames(Map.of("#gsi1pk", GSI1_PK))
        .expressionAttributeValues(Map.of(":gsi1pk", AttributeValue.builder().s(gsi1Pk).build()))
        .limit(page.limit());
    
    // Add exclusive start key if continuing from previous page
    if (exclusiveStartKey != null) {
        requestBuilder.exclusiveStartKey(exclusiveStartKey);
    }
    
    QueryResponse response = client.query(requestBuilder.build());
    
    // Extract document set IDs from GSI results
    Set<DocumentSetId> documentSetIds = new HashSet<>();
    for (Map<String, AttributeValue> item : response.items()) {
        if (item.containsKey("documentSetId")) {
            documentSetIds.add(DocumentSetId.fromString(item.get("documentSetId").s()));
        }
    }
    
    // Fetch full document sets (batch operation for efficiency)
    List<DocumentSet> documentSets = new ArrayList<>();
    for (DocumentSetId docSetId : documentSetIds) {
        findById(docSetId).ifPresent(documentSets::add);
    }
    
    // Encode next token if more results available
    String nextToken = PaginationTokenCodec.encode(response.lastEvaluatedKey());
    
    return PaginatedResult.of(documentSets, nextToken);
}
```

### 3.4 API Layer

#### 3.4.1 Controller Update

Update `DocumentSetController` to add paginated endpoint:

```java
/**
 * Lists document sets with pagination support.
 * 
 * @param limit optional page size (1-100, default 20)
 * @param nextToken optional continuation token from previous page
 * @return paginated list of document sets
 */
@GetMapping
@Operation(summary = "List document sets", 
           description = "Retrieves document sets with pagination. Use nextToken from response to fetch subsequent pages.")
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Document sets retrieved successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid limit or pagination token")
})
public ResponseEntity<PaginatedResponse<DocumentSetResponse>> listDocumentSets(
        @Parameter(description = "Page size (1-100, default 20)") 
        @RequestParam(required = false) Integer limit,
        @Parameter(description = "Continuation token from previous page") 
        @RequestParam(required = false) String nextToken) {
    
    // Create query
    ListDocumentSetsQuery query = ListDocumentSetsQuery.of(limit, nextToken);
    
    // Execute query
    PaginatedResult<DocumentSet> result = queryHandler.handle(query);
    
    // Map domain result to API response
    PaginatedResult<DocumentSetResponse> mappedResult = result.map(this::mapToResponse);
    PaginatedResponse<DocumentSetResponse> response = PaginatedResponse.from(mappedResult);
    
    return ResponseEntity.ok(response);
}
```

Note: Add `DocumentSetQueryHandler` as a dependency to the controller:

```java
@RestController
@RequestMapping("/api/document-sets")
@RequiredArgsConstructor
public class DocumentSetController {
    private final DocumentSetCommandHandler commandHandler;
    private final DocumentSetRepository repository;
    private final DocumentSetQueryHandler queryHandler;  // NEW
    // ...
}
```

#### 3.4.2 Exception Handler Update

Add to `DocumentExceptionHandler`:

```java
@ExceptionHandler(InvalidPaginationTokenException.class)
public ResponseEntity<ErrorResponse> handleInvalidPaginationToken(
        InvalidPaginationTokenException ex) {
    ErrorResponse error = new ErrorResponse(
        "INVALID_PAGINATION_TOKEN",
        "The provided pagination token is invalid or expired",
        Instant.now()
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
}

@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ErrorResponse> handleIllegalArgument(
        IllegalArgumentException ex) {
    ErrorResponse error = new ErrorResponse(
        "INVALID_PARAMETER",
        ex.getMessage(),
        Instant.now()
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
}
```

## 4. Data Seeding

### 4.1 Seed Data Utility

```java
package com.example.documents.infrastructure.seed;

import com.example.documents.application.command.CreateDocumentSetCommand;
import com.example.documents.application.handler.DocumentSetCommandHandler;
import com.example.documents.domain.model.*;
import com.example.documents.domain.repository.ContentStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Random;

/**
 * Seeds sample document sets for testing pagination.
 * 
 * <p>Automatically enabled in local profile. Can be controlled via
 * application property: documents.seed.enabled=true/false</p>
 */
@Component
@ConditionalOnProperty(
    name = "documents.seed.enabled", 
    havingValue = "true",
    matchIfMissing = false
)
@RequiredArgsConstructor
@Slf4j
public class DocumentSetSeeder implements CommandLineRunner {
    
    private final DocumentSetCommandHandler commandHandler;
    private final ContentStore contentStore;
    private final Random random = new Random();
    
    @Override
    public void run(String... args) {
        log.info("Starting document set seeding...");
        
        int totalSets = 50;
        int invoiceCount = 20;
        int orderCount = 15;
        int quotationCount = 10;
        int creditNoteCount = 5;
        
        seedDocumentSets(DocumentType.INVOICE, invoiceCount, "Invoice Set");
        seedDocumentSets(DocumentType.ORDER, orderCount, "Order Set");
        seedDocumentSets(DocumentType.QUOTATION, quotationCount, "Quotation Set");
        seedDocumentSets(DocumentType.CREDIT_NOTE, creditNoteCount, "Credit Note Set");
        
        log.info("Seeded {} document sets successfully", totalSets);
    }
    
    private void seedDocumentSets(DocumentType type, int count, String namePrefix) {
        for (int i = 1; i <= count; i++) {
            try {
                createSampleDocumentSet(type, namePrefix, i);
            } catch (Exception e) {
                log.error("Failed to seed document set {} {}: {}", namePrefix, i, e.getMessage());
            }
        }
    }
    
    private void createSampleDocumentSet(DocumentType type, String namePrefix, int number) {
        // Create sample content
        String sampleContent = generateSampleContent(type, number);
        Content content = Content.of(
            sampleContent.getBytes(StandardCharsets.UTF_8),
            Format.JSON
        );
        
        // Create schema reference (using dummy schema for now)
        SchemaVersionRef schemaRef = SchemaVersionRef.of(
            new SchemaId("sample-schema-" + type.name().toLowerCase()),
            VersionIdentifier.of("1.0.0")
        );
        
        // Create metadata
        Map<String, String> metadata = Map.of(
            "name", String.format("%s %03d", namePrefix, number),
            "description", generateDescription(type, number),
            "category", type.name(),
            "fiscalYear", "2024"
        );
        
        // Create command
        CreateDocumentSetCommand command = new CreateDocumentSetCommand(
            type,
            schemaRef,
            content,
            "seeder",
            metadata
        );
        
        // Execute
        commandHandler.handle(command);
        
        log.debug("Created {} {}", namePrefix, number);
    }
    
    private String generateSampleContent(DocumentType type, int number) {
        return String.format("""
            {
              "documentType": "%s",
              "documentNumber": "%s-%03d",
              "issueDate": "%s",
              "description": "Sample %s document for testing"
            }
            """,
            type.name(),
            type.name().substring(0, 3),
            number,
            Instant.now().minus(random.nextInt(90), ChronoUnit.DAYS),
            type.name().toLowerCase()
        );
    }
    
    private String generateDescription(DocumentType type, int number) {
        String[] templates = {
            "Q4 2024 %s for customer ABC-%03d",
            "Annual %s processing batch %03d",
            "Standard %s document %03d",
            "Automated %s generation %03d"
        };
        String template = templates[random.nextInt(templates.length)];
        return String.format(template, type.name().toLowerCase(), number);
    }
}
```

### 4.2 Configuration

Add to `application-documents.yml`:

```yaml
documents:
  seed:
    enabled: false  # Default: disabled for production safety
```

Create `application-local.yml` for local development:

```yaml
spring:
  profiles:
    active: local

# Local DynamoDB configuration
aws:
  dynamodb:
    endpoint: http://localhost:8000

# Enable data seeding in local environment
documents:
  seed:
    enabled: true
```

**Profile Strategy:**
- **Production/Default**: Seeding disabled by default
- **Local Profile**: Seeding automatically enabled when running with `--spring.profiles.active=local`
- **Override**: Can be explicitly disabled even in local with `--documents.seed.enabled=false`

**Running Locally:**
```bash
# Seeding enabled automatically
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Or via environment variable
export SPRING_PROFILES_ACTIVE=local
./mvnw spring-boot:run

# Disable seeding in local if needed
./mvnw spring-boot:run -Dspring-boot.run.profiles=local -Ddocuments.seed.enabled=false
```

### 4.3 Idempotency

The seeder should check if data already exists before seeding:

```java
@Override
public void run(String... args) {
    // Check if data already exists
    Page firstPage = Page.first(1);
    PaginatedResult<DocumentSet> existing = repository.findAll(firstPage);
    
    if (!existing.isEmpty()) {
        log.info("Document sets already exist ({}), skipping seeding", existing.size());
        return;
    }
    
    log.info("Starting document set seeding...");
    
    int totalSets = 50;
    int invoiceCount = 20;
    int orderCount = 15;
    int quotationCount = 10;
    int creditNoteCount = 5;
    
    seedDocumentSets(DocumentType.INVOICE, invoiceCount, "Invoice Set");
    seedDocumentSets(DocumentType.ORDER, orderCount, "Order Set");
    seedDocumentSets(DocumentType.QUOTATION, quotationCount, "Quotation Set");
    seedDocumentSets(DocumentType.CREDIT_NOTE, creditNoteCount, "Credit Note Set");
    
    log.info("Seeded {} document sets successfully", totalSets);
}
```

This ensures:
- Seeding only runs once (first startup)
- Restarting the application doesn't duplicate data
- Safe to leave enabled in local profile

### 4.4 Developer Experience

**First Time Setup:**
1. Start local DynamoDB: `docker run -p 8000:8000 amazon/dynamodb-local`
2. Run application: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
3. Seeder automatically creates 50 document sets on first startup
4. Test pagination: `curl "http://localhost:8080/api/document-sets?limit=10"`

**Subsequent Runs:**
- Seeder detects existing data and skips
- To reset data: stop app, delete DynamoDB data, restart

**CI/CD Environments:**
- Use `dev`, `staging`, `prod` profiles (not `local`)
- Seeding remains disabled by default
- Explicit opt-in required via configuration

## 5. Testing Strategy

### 5.1 Unit Tests

#### 5.1.1 Support Module Tests

**Page Test** (`support/common`):

```java
@Test
void pageValidatesPositiveLimit() {
    assertThatThrownBy(() -> new Page(0, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
    
    assertThatThrownBy(() -> new Page(-1, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
}

@Test
void pageFactoryMethods() {
    Page first = Page.first();
    assertThat(first.limit()).isEqualTo(20);
    assertThat(first.isFirstPage()).isTrue();
    
    Page next = Page.next(10, "token");
    assertThat(next.limit()).isEqualTo(10);
    assertThat(next.isFirstPage()).isFalse();
}
```

**PaginatedResult Test** (`support/common`):

```java
@Test
void paginatedResultMapTransformsItems() {
    PaginatedResult<Integer> numbers = PaginatedResult.of(List.of(1, 2, 3), "token");
    PaginatedResult<String> strings = numbers.map(Object::toString);
    
    assertThat(strings.items()).containsExactly("1", "2", "3");
    assertThat(strings.continuationToken()).hasValue("token");
}

@Test
void paginatedResultFactoryMethods() {
    PaginatedResult<String> lastPage = PaginatedResult.lastPage(List.of("a", "b"));
    assertThat(lastPage.hasMore()).isFalse();
    
    PaginatedResult<String> empty = PaginatedResult.empty();
    assertThat(empty.isEmpty()).isTrue();
    assertThat(empty.hasMore()).isFalse();
}
```

#### 5.1.2 PaginationTokenCodec Test

```java
@Test
void encodeAndDecodeRoundTrip() {
    Map<String, AttributeValue> original = Map.of(
        "PK", AttributeValue.builder().s("TENANT#DEFAULT#DOCSETS").build(),
        "SK", AttributeValue.builder().s("2024-01-15T10:30:00Z#docset-123").build()
    );
    
    String token = PaginationTokenCodec.encode(original);
    Map<String, AttributeValue> decoded = PaginationTokenCodec.decode(token);
    
    assertThat(decoded).isEqualTo(original);
}

@Test
void decodeInvalidTokenThrowsException() {
    assertThatThrownBy(() -> PaginationTokenCodec.decode("invalid-token"))
        .isInstanceOf(InvalidPaginationTokenException.class);
}
```

#### 5.1.3 ListDocumentSetsQuery Test

```java
@Test
void queryValidatesLimitRange() {
    assertThatThrownBy(() -> ListDocumentSetsQuery.of(0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and 100");
    
    assertThatThrownBy(() -> ListDocumentSetsQuery.of(101, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and 100");
}

@Test
void queryUsesDefaultLimit() {
    var query = ListDocumentSetsQuery.of(null, null);
    assertThat(query.page().limit()).isEqualTo(20);
}

@Test
void queryCreatesCorrectPageObject() {
    var query = ListDocumentSetsQuery.of(50, "token123");
    assertThat(query.page().limit()).isEqualTo(50);
    assertThat(query.page().continuationToken()).hasValue("token123");
}
```

### 5.2 Integration Tests

#### 5.2.1 Repository Pagination Test

```java
@Test
void findAllReturnsPaginatedResults() {
    // Seed 30 document sets
    for (int i = 0; i < 30; i++) {
        repository.save(createTestDocumentSet());
    }
    
    // First page
    Page page1 = Page.first(10);
    PaginatedResult<DocumentSet> result1 = repository.findAll(page1);
    assertThat(result1.items()).hasSize(10);
    assertThat(result1.hasMore()).isTrue();
    
    // Second page
    Page page2 = Page.next(10, result1.continuationToken().get());
    PaginatedResult<DocumentSet> result2 = repository.findAll(page2);
    assertThat(result2.items()).hasSize(10);
    assertThat(result2.hasMore()).isTrue();
    
    // Third page (last)
    Page page3 = Page.next(10, result2.continuationToken().get());
    PaginatedResult<DocumentSet> result3 = repository.findAll(page3);
    assertThat(result3.items()).hasSize(10);
    assertThat(result3.hasMore()).isFalse();
}
```

#### 5.2.2 Controller Pagination Test

```java
@Test
void listDocumentSetsReturnsPaginatedResponse() throws Exception {
    // Seed data
    seedDocumentSets(25);
    
    // First page
    mockMvc.perform(get("/api/document-sets?limit=10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items.length()").value(10))
        .andExpect(jsonPath("$.nextToken").exists());
}

@Test
void listDocumentSetsWithInvalidLimitReturnsBadRequest() throws Exception {
    mockMvc.perform(get("/api/document-sets?limit=200"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
}

@Test
void listDocumentSetsWithInvalidTokenReturnsBadRequest() throws Exception {
    mockMvc.perform(get("/api/document-sets?nextToken=invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PAGINATION_TOKEN"));
}
```

### 5.3 Property-Based Tests

#### 5.3.1 Pagination Completeness Property

**Validates: Requirements 2.1, 2.3**

```java
@Property
void paginationReturnsAllItemsExactlyOnce(@ForAll @IntRange(min = 1, max = 100) int pageSize) {
    // Seed known number of document sets
    int totalSets = 50;
    List<DocumentSetId> seededIds = seedDocumentSets(totalSets);
    
    // Paginate through all results
    Set<DocumentSetId> retrievedIds = new HashSet<>();
    Optional<String> nextToken = Optional.empty();
    int pageCount = 0;
    
    do {
        Page page = nextToken.isPresent() 
            ? Page.next(pageSize, nextToken.get())
            : Page.first(pageSize);
            
        PaginatedResult<DocumentSet> result = repository.findAll(page);
        result.items().forEach(ds -> retrievedIds.add(ds.id()));
        nextToken = result.continuationToken();
        pageCount++;
    } while (nextToken.isPresent());
    
    // Verify all items retrieved exactly once
    assertThat(retrievedIds).containsExactlyInAnyOrderElementsOf(seededIds);
    assertThat(pageCount).isGreaterThan(0);
}
```

#### 5.3.2 Token Encoding Stability Property

**Validates: Requirement 2.2**

```java
@Property
void encodedTokensAreStableAndReversible(@ForAll("dynamoDbKeys") Map<String, AttributeValue> key) {
    String token1 = PaginationTokenCodec.encode(key);
    String token2 = PaginationTokenCodec.encode(key);
    
    // Same input produces same token
    assertThat(token1).isEqualTo(token2);
    
    // Token can be decoded back to original
    Map<String, AttributeValue> decoded = PaginationTokenCodec.decode(token1);
    assertThat(decoded).isEqualTo(key);
}

@Provide
Arbitrary<Map<String, AttributeValue>> dynamoDbKeys() {
    return Arbitraries.maps(
        Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(20),
        Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(50)
            .map(s -> AttributeValue.builder().s(s).build())
    ).ofMinSize(1).ofMaxSize(5);
}
```

## 6. Performance Considerations

### 6.1 Query Optimization

- Uses GSI1 Query (not Scan) for O(log n) lookup time
- Limit parameter controls DynamoDB read capacity consumption
- Projection of only required attributes reduces data transfer

### 6.2 Batch Fetching

After retrieving document set IDs from GSI1, full document sets are fetched in batch:
- Consider implementing BatchGetItem for efficiency
- Current implementation uses sequential findById calls
- Trade-off: simplicity vs performance for large page sizes

### 6.3 Caching Strategy (Future)

Not implemented in this design, but consider:
- Cache first page results (most frequently accessed)
- Short TTL (30-60 seconds) to balance freshness and performance
- Invalidate on document set creation

## 7. Security Considerations

### 7.1 Token Tampering

- Tokens are Base64-encoded but not signed
- Invalid tokens result in DynamoDB query errors (caught and handled)
- Consider HMAC signing for additional security if needed

### 7.2 Tenant Isolation

- All queries include tenant ID in GSI1 partition key
- Tokens cannot cross tenant boundaries
- Repository enforces tenant context

## 8. API Documentation

### 8.1 OpenAPI Specification

```yaml
/api/document-sets:
  get:
    summary: List document sets with pagination
    parameters:
      - name: limit
        in: query
        schema:
          type: integer
          minimum: 1
          maximum: 100
          default: 20
        description: Number of items per page
      - name: nextToken
        in: query
        schema:
          type: string
        description: Continuation token from previous response
    responses:
      '200':
        description: Paginated list of document sets
        content:
          application/json:
            schema:
              type: object
              properties:
                items:
                  type: array
                  items:
                    $ref: '#/components/schemas/DocumentSetResponse'
                nextToken:
                  type: string
                  nullable: true
      '400':
        description: Invalid parameters
```

## 9. Migration Strategy

### 9.1 Backwards Compatibility

- Existing `GET /document-sets` endpoint remains functional
- Default limit of 20 applied when no parameters provided
- Clients not using pagination continue to work

### 9.2 Deprecation Path

1. Add new paginated endpoint (this design)
2. Update documentation to recommend pagination
3. Monitor usage of non-paginated calls
4. Eventually deprecate unlimited listing (future)

## 10. Monitoring and Observability

### 10.1 Metrics

- Page request count by limit size
- Token decode failure rate
- Average response time by page size
- DynamoDB read capacity consumption

### 10.2 Logging

- Log pagination parameters (limit, has token)
- Log page result size and continuation status
- Log token decode failures with sanitized token prefix

## 11. Correctness Properties

### 11.1 Pagination Completeness

**Property**: Paginating through all results returns every item exactly once.

**Test Strategy**: Property-based test with varying page sizes

### 11.2 Token Stability

**Property**: Encoding the same key multiple times produces identical tokens.

**Test Strategy**: Property-based test with generated DynamoDB keys

### 11.3 No Data Loss

**Property**: Total items across all pages equals total items in repository.

**Test Strategy**: Integration test with known dataset size

### 11.4 Order Consistency

**Property**: Items appear in consistent order across pagination boundaries.

**Test Strategy**: Verify GSI1 sort key ordering is maintained

## 12. Future Enhancements

### 12.1 Filtering

Add query parameters for filtering by:
- Document type
- Date range
- Metadata fields

### 12.2 Sorting

Support custom sort orders:
- Creation date (ascending/descending)
- Name alphabetically
- Document count

### 12.3 Cursor-Based Pagination

Alternative to token-based pagination:
- Use document set ID as cursor
- More predictable for clients
- Requires different GSI design
