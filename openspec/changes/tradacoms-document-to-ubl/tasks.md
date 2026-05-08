## 1. Setup

- [ ] 1.1 Confirm the first supported TRADACOMS message shape, add any missing parser or XML dependencies, and create reusable inbound fixtures for valid and invalid source payloads.
- [ ] 1.2 Add failing example-driven tests for inbound TRADACOMS ingestion, document metadata retrieval, derivative listing, source-content download, and derivative-content download.

## 2. Core Implementation

- [ ] 2.1 Implement the inbound TRADACOMS request and result models plus the `InboundTradacomsIngestionService` orchestration flow.
- [ ] 2.2 Implement the first-slice `TradacomsMessageParser` and `CanonicalDocumentModel` mapping for the supported TRADACOMS message.
- [ ] 2.3 Implement the `TradacomsToUblDerivationService` to serialise the canonical model into UBL XML with a deterministic `TRADACOMS_TO_UBL` transformation label.
- [ ] 2.4 Extend the documents application flow to store source versions as `Format.EDI`, create linked UBL derivatives as `Format.XML`, and record translation failure status without losing the stored source document.

## 3. API Integration

- [ ] 3.1 Implement `DocumentContentQueryService` for resolving document-version and derivative content from the existing `ContentStore`.
- [ ] 3.2 Add `GET /api/document-sets/{setId}/documents/{docId}/versions/{versionNumber}/content` and `GET /api/document-sets/{setId}/documents/{docId}/derivatives/{derivativeId}/content` to the existing `DocumentSetController`.
- [ ] 3.3 Update document and derivative response mappings so source versions expose `Format.EDI`, UBL derivatives expose `Format.XML`, and derivative metadata surfaces the `TRADACOMS_TO_UBL` transformation.
- [ ] 3.4 Wire the existing inbound file processing entry point to the new TRADACOMS ingestion flow so processed files are registered in the documents domain.

## 4. Property-Based Tests

- [ ] 4.1 Implement property: Source payload round trip preserves bytes and format using jqwik
  - Generator: valid supported TRADACOMS payloads rendered from constrained message builders with varied identifiers, dates, optional fields, and line-item counts
  - Test: ingest source payloads, retrieve source-version content, and assert byte-for-byte equality plus `Format.EDI`
  - **Validates: Requirements 1.1, 1.2, 1.3, 4.1, 4.3**

- [ ] 4.2 Implement property: Successful derivation produces one linked XML derivative using jqwik
  - Generator: valid supported TRADACOMS payloads that map cleanly into the canonical model
  - Test: ingest payloads and assert exactly one derivative, correct source-version linkage, `TRADACOMS_TO_UBL`, and `Format.XML`
  - **Validates: Requirements 2.1, 2.2, 2.3, 3.3**

- [ ] 4.3 Implement property: Canonical model survives TRADACOMS to UBL round trip using jqwik
  - Generator: canonical-model instances constrained to the supported first-slice message shape, including edge dates, optional values, and line-item variations
  - Test: serialise to TRADACOMS, parse, derive UBL, reparse UBL, and assert equivalence for all mapped business fields
  - **Validates: Requirements 2.4**

- [ ] 4.4 Implement property: Derivative content round trip preserves bytes and XML identity using jqwik
  - Generator: successfully derived UBL outputs produced from generated canonical-model instances
  - Test: retrieve derivative content through the content query path and assert byte-for-byte equality plus `Format.XML`
  - **Validates: Requirements 4.2, 4.4**

- [ ] 4.5 Implement property: Translation failure preserves source evidence and suppresses false success using jqwik
  - Generator: storable but unmappable TRADACOMS payloads created by mutating valid fixtures to remove required mapped fields or introduce unsupported combinations
  - Test: ingest failing payloads and assert stored source evidence, non-success translation status with errors, and no successful UBL derivative exposure
  - **Validates: Requirements 5.1, 5.2, 5.3**

## 5. Verification and Review

- [ ] 5.1 Run the relevant module test suites, including jqwik property tests, and fix any regressions in ingestion, transformation, or content retrieval behavior.
- [ ] 5.2 Conduct code review using `@code-reviewer`.
- [ ] 5.3 Address code review findings and refresh any OpenAPI or module documentation changed by the new content endpoints.
