## 1. Setup

- [ ] 1.1 Create a new frontend application module for the EDI operations workbench with React, TypeScript, routing, `shadcn/ui`, and local development scripts.
- [ ] 1.2 Add frontend dependencies for data fetching, form handling, OpenAPI client generation, component testing, and `fast-check` property-based testing.
- [ ] 1.3 Configure local development so the frontend can resolve the backend default URL and optional override URL for browser-based development.

## 2. Integration Foundation

- [ ] 2.1 Implement the frontend integration layer for backend URL resolution, OpenAPI contract loading, and request lifecycle state management.
- [ ] 2.2 Generate or refresh a typed API client from the local `/api-docs` contract and wrap the initial schema and document-set operations used by workflow views.
- [ ] 2.3 Implement the API explorer projection that turns the OpenAPI document into operation summaries and detail models.

## 3. Workbench Shell and Retail Journeys

- [ ] 3.1 Build the global application shell with persistent navigation, page chrome, connection status, and retry affordances.
- [ ] 3.2 Implement the dashboard and curated UK retail journey views for `ORDERS`, `DESADV`, `RECADV`, and `INVOIC`, including planned-step rendering for unsupported backend capabilities.
- [ ] 3.3 Implement reusable empty, loading, error, and planned-capability states used across shell and journey views.

## 4. Schema and Document Workflow Views

- [ ] 4.1 Implement schema creation and schema-version forms with local required-field validation and success or failure result handling.
- [ ] 4.2 Implement document-set list and detail views with continuation controls driven by backend pagination data.
- [ ] 4.3 Implement create document set, add document, add version, create derivative, and validation workflow views using live backend responses.
- [ ] 4.4 Implement document version inspection and raw-content download handling, including recoverable error states for missing content.

## 5. Property-Based Tests

- [ ] 5.1 Implement property: Shell state projection remains consistent across routes using `fast-check`
  - Generator: valid primary route keys and all integration-state variants
  - Test: shared navigation and shell status regions remain present while integration state projects correctly
  - **Validates: Requirements W1.1, W1.2, W1.3, I4.1, I4.2**

- [ ] 5.2 Implement property: Retail journey mapping reflects supported and planned steps using `fast-check`
  - Generator: journey definitions, capability maps, and linked record counts
  - Test: journey steps are marked available or planned correctly and preserve linked record counts
  - **Validates: Requirements W2.1, W2.2, W2.4**

- [ ] 5.3 Implement property: Schema workflow validation and recovery preserve user intent using `fast-check`
  - Generator: valid and invalid schema form states plus success and failure backend responses
  - Test: invalid states are rejected early, success preserves identifiers, and failures preserve entered values with error details
  - **Validates: Requirements W3.2, W3.3, W3.4**

- [ ] 5.4 Implement property: Document workflow projection preserves document evidence using `fast-check`
  - Generator: document-set payloads with documents, versions, derivatives, schema references, and validation results
  - Test: workflow view models preserve document evidence, validation outcomes, and actionable error information
  - **Validates: Requirements W4.3, W4.4, W5.1, W5.2**

- [ ] 5.5 Implement property: Pagination tokens are preserved as opaque continuation state using `fast-check`
  - Generator: paginated document-set responses with optional `nextToken` and `nextUrl`
  - Test: continuation controls preserve ordering and forward backend continuation data without reinterpretation
  - **Validates: Requirements W4.2**

- [ ] 5.6 Implement property: Download descriptors preserve backend content identity using `fast-check`
  - Generator: arbitrary byte arrays, media types, file-name metadata, and document version references
  - Test: generated download descriptors preserve bytes and the associated document version identity
  - **Validates: Requirements W5.3**

- [ ] 5.7 Implement property: Local backend configuration resolution is deterministic using `fast-check`
  - Generator: default and override configuration combinations
  - Test: override precedence and failure-state base URL preservation hold for all generated configurations
  - **Validates: Requirements I1.1, I1.3, I1.4**

- [ ] 5.8 Implement property: OpenAPI explorer projection is contract-complete using `fast-check`
  - Generator: synthetic OpenAPI operation maps with varying summaries, tags, and schema metadata
  - Test: explorer models contain every operation exactly once with the required metadata for list and detail views
  - **Validates: Requirements I2.1, I2.2, I2.3, I2.4**

- [ ] 5.9 Implement property: Request lifecycle transitions are recoverable using `fast-check`
  - Generator: request lifecycle event sequences over connected, pending, failed, retry, and recovery states
  - Test: failed operations preserve retry context and can transition back to pending or connected without full reload
  - **Validates: Requirements I3.2, I3.3, I4.3, I4.4**

## 6. Verification and Review

- [ ] 6.1 Add focused unit and component tests for route presence, empty states, contract-load failures, missing content errors, and blocked submissions when the backend is unreachable.
- [ ] 6.2 Run the frontend test suite, property-based tests, linting, and production build locally.
- [ ] 6.3 Conduct code review using `@code-reviewer` and address the findings.
- [ ] 6.4 Update developer documentation with frontend startup steps, local backend expectations, and the OpenAPI auto-wiring flow.
