## 1. Setup

- [x] 1.1 Confirm the first supported TRADACOMS message shape, add any missing parser dependencies, and create reusable inbound fixtures for valid, invalid, and unsupported source payloads.
- [x] 1.2 Add failing example-driven tests for inbound TRADACOMS ingestion, document metadata retrieval, and source-content download.

## 2. Core Implementation

- [x] 2.1 Implement the inbound TRADACOMS request and result models plus the `InboundTradacomsIngestionService` orchestration flow.
- [x] 2.2 Implement the first-slice `TradacomsMessageParser` for the supported TRADACOMS message and return structured parse results.
- [x] 2.3 Extend the documents application flow to store source versions as `Format.EDI` and record parse status, identified message type, and parse errors without losing the stored source document.

## 3. API Integration

- [x] 3.1 Implement `DocumentContentQueryService` for resolving document-version content from the existing `ContentStore`.
- [x] 3.2 Add `GET /api/document-sets/{setId}/documents/{docId}/versions/{versionNumber}/content` to the existing `DocumentSetController`.
- [x] 3.3 Update document and version response mappings so source versions expose `Format.EDI` and parse metadata.
- [x] 3.4 Wire the existing inbound file processing entry point to the new TRADACOMS ingestion flow so processed files are registered in the documents domain.

## 4. Property-Based Tests

- [x] 4.1 Implement property: Source payload round trip preserves bytes and format using jqwik
  - Generator: valid supported TRADACOMS payloads rendered from constrained message builders with varied identifiers, dates, optional fields, and line-item counts
  - Test: ingest source payloads, retrieve source-version content, and assert byte-for-byte equality plus `Format.EDI`
  - **Validates: Requirements 1.1, 1.2, 1.3, 4.1, 4.2**

- [x] 4.2 Implement property: Successful parsing records source metadata consistently using jqwik
  - Generator: valid supported TRADACOMS payloads that parse cleanly into the first-slice parser result
  - Test: ingest payloads and assert `SUCCESS` parse status plus correct identified message type on stored document metadata
  - **Validates: Requirements 2.1, 2.2, 3.1**

- [x] 4.3 Implement property: Parse failures preserve source evidence and surface errors using jqwik
  - Generator: storable but invalid or unsupported TRADACOMS payloads created by mutating valid fixtures to break syntax or introduce unsupported message shapes
  - Test: ingest failing payloads and assert stored source evidence, non-success parse status, and surfaced parse errors
  - **Validates: Requirements 2.3, 5.1, 5.2**

## 5. Verification and Review

- [x] 5.1 Run the relevant module test suites, including jqwik property tests, and fix any regressions in ingestion, parsing, or content retrieval behaviour.
- [x] 5.2 Conduct code review using `@code-reviewer`.
- [x] 5.3 Address code review findings and refresh any OpenAPI or module documentation changed by the new content endpoint.
