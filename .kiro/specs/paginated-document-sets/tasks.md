# Tasks: Paginated Document Sets Listing

## 1. Create Support Module Infrastructure

### 1.1 Create support/common module structure
- [x] Create `support/common/pom.xml` with dependencies (Lombok, JUnit, jqwik, AssertJ)
- [x] Update parent `pom.xml` to include `support/common` module
- [x] Create package structure: `com.example.common.pagination` and `com.example.common.api`

**Requirements**: Foundation for Requirements 2.1, 2.2

### 1.2 Implement Page record
- [x] Create `com.example.common.pagination.Page` record with limit and continuationToken
- [x] Add validation for positive limit in compact constructor
- [x] Implement factory methods: `first()`, `first(int)`, `next(int, String)`
- [x] Add `isFirstPage()` helper method
- [x] Write unit tests for Page validation and factory methods

**Requirements**: Requirement 2.1 (pagination parameters)

### 1.3 Implement PaginatedResult record
- [x] Create `com.example.common.pagination.PaginatedResult<T>` record with items and continuationToken
- [x] Add null check for items in compact constructor
- [x] Implement helper methods: `hasMore()`, `size()`, `isEmpty()`
- [x] Implement `map(Function<T, R>)` for type transformation
- [x] Implement factory methods: `of()`, `lastPage()`, `empty()`
- [x] Write unit tests for PaginatedResult operations and transformations

**Requirements**: Requirement 2.1 (paginated response structure)

### 1.4 Implement PaginatedResponse record
- [x] Create `com.example.common.api.PaginatedResponse<T>` record with items and nextToken
- [x] Add `@JsonInclude(JsonInclude.Include.NON_NULL)` for nextToken
- [x] Add null check for items in compact constructor
- [x] Implement static factory method `from(PaginatedResult<T>)`
- [x] Write unit tests for PaginatedResponse creation and JSON serialization

**Requirements**: Requirement 2.1, 2.2 (API response format with opaque tokens)

## 2. Update Domain Layer

### 2.1 Add support/common dependency to documents domain
- [x] Update `domains/documents/pom.xml` to depend on `support/common` module
- [x] Verify dependency resolution with `mvn clean compile`

**Requirements**: Foundation for all pagination requirements

### 2.2 Update DocumentSetRepository interface
- [x] Add `PaginatedResult<DocumentSet> findAll(Page page)` method to DocumentSetRepository
- [x] Add Javadoc explaining pagination behavior
- [x] Keep existing `List<DocumentSet> findAll()` method for backwards compatibility

**Requirements**: Requirement 2.1, 2.3 (repository pagination support)

## 3. Implement Application Layer

### 3.1 Create ListDocumentSetsQuery
- [x] Create `com.example.documents.application.query` package
- [x] Create `ListDocumentSetsQuery` record with Page parameter
- [x] Implement static factory method `of(Integer limit, String nextToken)` with validation
- [x] Validate limit is between 1 and 100, default to 20 if null
- [x] Write unit tests for query validation and default values

**Requirements**: Requirement 2.1, 2.4 (query validation)

### 3.2 Create DocumentSetQueryHandler
- [x] Create `DocumentSetQueryHandler` service class
- [x] Inject DocumentSetRepository dependency
- [x] Implement `handle(ListDocumentSetsQuery)` method returning PaginatedResult<DocumentSet>
- [x] Add `@Service` annotation
- [x] Write unit tests with mock repository

**Requirements**: Requirement 2.1 (query execution)

### 3.3 Create InvalidPaginationTokenException
- [x] Create `InvalidPaginationTokenException` in application.handler package
- [x] Extend RuntimeException with message and cause constructor
- [x] Add Javadoc explaining when this exception is thrown

**Requirements**: Requirement 2.2, 2.4 (invalid token handling)

## 4. Implement Infrastructure Layer

### 4.1 Create PaginationTokenCodec
- [x] Create `PaginationTokenCodec` utility class in infrastructure.persistence package
- [x] Implement `encode(Map<String, AttributeValue>)` returning Base64-encoded String
- [x] Implement `decode(String)` returning Map<String, AttributeValue>
- [x] Handle null/empty inputs appropriately
- [x] Throw InvalidPaginationTokenException for malformed tokens
- [x] Write unit tests for encode/decode round-trip and error cases

**Requirements**: Requirement 2.2 (opaque token encoding)

### 4.2 Implement paginated findAll in DynamoDbDocumentSetRepository
- [x] Implement `findAll(Page page)` method in DynamoDbDocumentSetRepository
- [x] Decode continuation token using PaginationTokenCodec
- [x] Build QueryRequest with GSI1, limit, and exclusiveStartKey
- [x] Execute Query operation (NOT Scan)
- [x] Extract document set IDs from GSI results
- [x] Batch fetch full document sets using existing findById
- [x] Encode LastEvaluatedKey using PaginationTokenCodec
- [x] Return PaginatedResult with items and encoded token
- [x] Write integration tests with local DynamoDB for pagination

**Requirements**: Requirement 2.1, 2.2, 2.3 (DynamoDB pagination with Query)

**Details**:
- Add pagination-specific tests to DynamoDbDocumentSetRepositoryIntegrationTest
- Test multiple pages, last page without token, empty results

## 5. Update API Layer

### 5.1 Update DocumentSetController with paginated endpoint
- [x] Inject DocumentSetQueryHandler into DocumentSetController
- [x] Add GET /api/document-sets endpoint with limit and nextToken query parameters
- [x] Create ListDocumentSetsQuery from parameters
- [x] Execute query via handler
- [x] Map PaginatedResult<DocumentSet> to PaginatedResult<DocumentSetResponse>
- [x] Convert to PaginatedResponse using from() method
- [x] Return ResponseEntity with 200 OK
- [x] Add OpenAPI annotations for documentation
- [x] Write controller unit tests with MockMvc for pagination endpoint

**Requirements**: Requirement 2.1, 4.1 (paginated API endpoint)

**Details**:
- Add tests to DocumentSetControllerTest for pagination scenarios
- Test valid pagination, invalid limit, invalid token

### 5.2 Update DocumentExceptionHandler
- [x] Add exception handler for InvalidPaginationTokenException
- [x] Return 400 Bad Request with error code "INVALID_PAGINATION_TOKEN"
- [x] Add exception handler for IllegalArgumentException (from query validation)
- [x] Return 400 Bad Request with error code "INVALID_PARAMETER"
- [x] Write unit tests for pagination exception handling

**Requirements**: Requirement 2.4, 4.3 (error handling)

**Details**:
- Add tests to DocumentExceptionHandlerTest for pagination exceptions

## 6. Data Seeding

### 6.1 Create DocumentSetSeeder component
- [x] Create `com.example.documents.infrastructure.seed` package
- [x] Create `DocumentSetSeeder` implementing CommandLineRunner
- [x] Add `@ConditionalOnProperty` for "documents.seed.enabled"
- [x] Inject DocumentSetCommandHandler and ContentStore
- [x] Implement idempotency check using repository.findAll(Page.first(1))
- [x] Implement seeding logic for 50 document sets (20 invoices, 15 orders, 10 quotations, 5 credit notes)
- [x] Generate realistic sample content and metadata
- [x] Add logging for seeding progress

**Requirements**: Requirement 2.5 (sample data for testing)

**Details**:
- Check if data exists before seeding (idempotent)
- Use DocumentSetCommandHandler to create sets
- Generate varied document types and sizes
- Log progress and completion

### 6.2 Add seeding configuration
- [x] Add `documents.seed.enabled: false` to application-documents.yml
- [x] Create application-local.yml with seed enabled and local DynamoDB endpoint
- [x] Document how to run with local profile in comments

**Requirements**: Requirement 2.5 (configurable seeding)

**Details**:
- Default: disabled for production safety
- Local profile: automatically enabled
- Override possible via command line

## 7. Testing

### 7.1 Write property-based tests for pagination completeness
- [x] Create PaginationCompletenessPropertyTest in infrastructure.persistence test directory
- [x] Implement property: paginating through all results returns every item exactly once
- [x] Use varying page sizes (1-100) with @IntRange
- [x] Seed known dataset and verify all items retrieved
- [x] Annotate with **Validates: Requirements 2.1, 2.3**

**Requirements**: Requirement 2.1, 2.3 (pagination correctness)

**Details**:
- Use jqwik @Property annotation
- Test with DynamoDB Local
- Verify no duplicates and no missing items

### 7.2 Write property-based tests for token stability
- [x] Create TokenEncodingPropertyTest in infrastructure.persistence test directory
- [x] Implement property: encoding same key produces identical tokens
- [x] Implement property: encoded tokens can be decoded back to original
- [x] Generate arbitrary DynamoDB key maps using @Provide
- [x] Annotate with **Validates: Requirement 2.2**

**Requirements**: Requirement 2.2 (token encoding correctness)

**Details**:
- Use jqwik @Property annotation
- Test with various AttributeValue combinations
- Verify round-trip encoding/decoding

### 7.3 Write integration tests for repository pagination
- [x] Add pagination tests to DynamoDbDocumentSetRepositoryIntegrationTest
- [x] Test pagination through multiple pages
- [x] Test last page has no continuation token
- [x] Test empty results
- [x] Test invalid token handling
- [x] Use local DynamoDB for integration testing

**Requirements**: All pagination requirements

**Details**:
- Seed 30+ document sets
- Paginate with different page sizes
- Verify token presence/absence

### 7.4 Write controller integration tests
- [x] Add pagination tests to DocumentSetControllerTest
- [x] Test GET /api/document-sets with pagination parameters
- [x] Test invalid limit returns 400
- [x] Test invalid token returns 400
- [x] Test response structure matches PaginatedResponse
- [x] Use MockMvc for controller testing

**Requirements**: Requirement 2.1, 2.4, 4.1, 4.3

**Details**:
- Mock DocumentSetQueryHandler
- Verify JSON response structure
- Test error responses

## 8. Documentation and Verification

### 8.1 Update API documentation
- [x] Verify OpenAPI spec includes pagination parameters
- [x] Verify response schema includes nextToken field
- [x] Add example requests and responses
- [x] Document pagination behavior in endpoint description

**Requirements**: Requirement 4.1 (API contract)

**Details**:
- Check OpenAPI UI at /swagger-ui.html
- Verify parameter descriptions
- Test example requests

### 8.2 Verify no SCAN operations
- [x] Review all DynamoDB operations to confirm Query is used
- [x] Add code comments explaining Query strategy
- [x] Document GSI1 usage for pagination

**Requirements**: Requirement 2.3 (no SCAN operations)

**Details**:
- Review DynamoDbDocumentSetRepository implementation
- Confirm GSI1 Query usage
- Document access pattern

### 8.3 End-to-end verification
- [~] Start application with local profile
- [~] Verify seeder creates 50 document sets
- [~] Test pagination through all pages via API
- [~] Verify nextToken is present/absent appropriately
- [~] Verify all 50 sets are retrievable through pagination
- [~] Measure response times (should be < 200ms)

**Requirements**: All requirements, success metrics

**Details**:
- Use curl or Postman for manual testing
- Verify complete pagination flow
- Check performance metrics

## Notes

- Core infrastructure (support module, domain/application layers) is complete
- Repository implementation is complete
- API endpoint is implemented
- Remaining work focuses on testing and data seeding
- Property-based tests will validate correctness properties
- Integration tests will verify end-to-end behavior
- Seeding provides realistic test data for verification
