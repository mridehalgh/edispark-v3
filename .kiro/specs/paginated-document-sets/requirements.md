# Requirements: Paginated Document Sets Listing

## 1. Overview

The current `GET /document-sets` endpoint returns all document sets without pagination, which will cause performance and scalability issues as the number of document sets grows. This feature adds proper pagination support with opaque continuation tokens that hide DynamoDB implementation details from API clients.

## 2. User Stories

### 2.1 As an API client, I want to retrieve document sets in pages
So that I can efficiently process large collections without overwhelming memory or network resources.

**Acceptance Criteria:**
- API accepts an optional `limit` query parameter (default: 20, max: 100)
- API accepts an optional `nextToken` query parameter for continuation
- Response includes a `nextToken` field when more results are available
- Response omits `nextToken` when no more results exist

### 2.2 As an API client, I want pagination tokens to be opaque
So that I don't need to understand DynamoDB internals and the implementation can change without breaking my code.

**Acceptance Criteria:**
- Pagination tokens are Base64-encoded strings
- Tokens are treated as opaque by clients (no parsing required)
- Invalid tokens return a clear 400 Bad Request error
- Token format can change without breaking existing clients

### 2.3 As a developer, I want to avoid DynamoDB SCAN operations
So that the system remains performant and cost-effective as data grows.

**Acceptance Criteria:**
- Implementation uses DynamoDB Query (not Scan)
- Query uses the existing GSI1 index for tenant-based listing
- Pagination uses DynamoDB's native `LastEvaluatedKey` mechanism
- No full table scans occur during pagination

### 2.4 As an API client, I want consistent error handling
So that I can reliably handle edge cases and invalid requests.

**Acceptance Criteria:**
- Invalid `limit` values (< 1 or > 100) return 400 Bad Request
- Invalid `nextToken` format returns 400 Bad Request with clear message
- Expired or corrupted tokens return 400 Bad Request
- Empty result sets return valid responses with empty items array

### 2.5 As a developer, I want sample data seeded for testing
So that I can verify pagination behaviour with realistic datasets and demonstrate the feature.

**Acceptance Criteria:**
- Seed script creates at least 50 document sets for testing pagination
- Sample data includes varied document types (invoices, orders, quotes, etc.)
- Sample data includes realistic metadata (names, descriptions, timestamps)
- Seed script is idempotent (can be run multiple times safely)
- Seed script can be run against local DynamoDB for development
- Sample data covers edge cases (empty sets, single document, multiple documents)

## 3. Non-Functional Requirements

### 3.1 Performance
- First page response time < 200ms for typical datasets
- Subsequent page response time < 200ms
- No performance degradation as total document set count grows

### 3.2 Scalability
- Support pagination through millions of document sets
- Consistent memory usage regardless of total dataset size
- Read capacity consumption proportional to page size, not total size

### 3.3 Security
- Pagination tokens do not expose sensitive data
- Tokens cannot be manipulated to access unauthorized data
- Tenant isolation maintained across pagination boundaries

## 4. API Contract

### 4.1 Request

```
GET /document-sets?limit={limit}&nextToken={token}
```

**Query Parameters:**
- `limit` (optional): Number of items per page (1-100, default: 20)
- `nextToken` (optional): Opaque continuation token from previous response

### 4.2 Response (200 OK)

```json
{
  "items": [
    {
      "id": "docset-123",
      "name": "Invoice Set",
      "description": "Q4 2024 invoices",
      "createdAt": "2024-01-15T10:30:00Z",
      "documents": [...]
    }
  ],
  "nextToken": "eyJQSyI6eyJTIjoiVEVOQU5UI..."
}
```

**Fields:**
- `items`: Array of document set objects (may be empty)
- `nextToken`: Opaque string for next page (omitted if no more results)

### 4.3 Error Responses

**400 Bad Request - Invalid Limit:**
```json
{
  "error": "INVALID_PARAMETER",
  "message": "Limit must be between 1 and 100",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**400 Bad Request - Invalid Token:**
```json
{
  "error": "INVALID_PAGINATION_TOKEN",
  "message": "The provided pagination token is invalid or expired",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## 5. Technical Constraints

### 5.1 DynamoDB Constraints
- Must use Query operation (SCAN is prohibited)
- Must use existing GSI1 index structure
- Token encoding must handle DynamoDB's `LastEvaluatedKey` format
- Must handle DynamoDB's 1MB query result size limit

### 5.2 Backwards Compatibility
- Existing `GET /document-sets` endpoint behaviour preserved when no pagination parameters provided
- Default limit ensures reasonable response sizes
- Clients not using pagination continue to work (with default limit applied)

## 6. Sample Data Requirements

### 6.1 Data Seeding Script

A data seeding utility must be created to populate test data for pagination verification.

**Script Requirements:**
- Command-line utility or Spring Boot component
- Configurable number of document sets to create (default: 50)
- Runs against configured DynamoDB endpoint (local or AWS)
- Idempotent operation (checks for existing data)
- Clear logging of seeding progress

### 6.2 Sample Data Characteristics

**Document Set Variety:**
- 20 invoice document sets (UBL Invoice format)
- 15 order document sets (UBL Order format)
- 10 quotation document sets (UBL Quotation format)
- 5 credit note document sets (UBL CreditNote format)

**Document Set Sizes:**
- 30 sets with single document
- 15 sets with 2-5 documents
- 5 sets with 6-10 documents

**Metadata Patterns:**
- Names: "{DocumentType} Set {Number}" (e.g., "Invoice Set 001")
- Descriptions: Realistic business descriptions
- Timestamps: Spread across last 90 days
- Document types: Match UBL document types from existing system

### 6.3 Sample Data Structure

Each seeded document set should include:
- Valid DocumentSetId
- Name and description
- At least one document with:
  - Valid DocumentId
  - Document type (INVOICE, ORDER, QUOTATION, CREDIT_NOTE)
  - At least one version with:
    - Content stored in ContentStore
    - Valid ContentHash
    - Format (JSON)
    - Timestamp

### 6.4 Verification

After seeding:
- Verify all 50 document sets are retrievable
- Verify pagination returns correct page sizes
- Verify nextToken allows traversal through all pages
- Verify final page has no nextToken

## 7. Out of Scope

- Filtering document sets by criteria (name, date range, etc.)
- Sorting options (always sorted by creation order)
- Cursor-based pagination (using DynamoDB's native pagination only)
- Caching of pagination results
- Pagination for nested collections (documents within a set)
- Production data migration or seeding

## 7. Dependencies

- Existing DynamoDB table with GSI1 index
- Existing `DocumentSetRepository` interface
- Existing `DocumentSetController` REST endpoint
- Existing `ContentStore` for document content storage
- Java Base64 encoding utilities
- Sample UBL document content (from ubl-source directory)

## 8. Success Metrics

- Zero SCAN operations in production logs
- Average response time < 200ms for paginated requests
- 100% of pagination tokens successfully decoded (no format errors)
- API clients successfully paginate through large result sets
- Seed script successfully creates 50+ document sets in < 30 seconds
- Pagination correctly handles all seeded data without errors
