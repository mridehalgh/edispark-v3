## 1. Setup

- [x] 1.1 Create the frontend application scaffold for the EDI operations console
- [x] 1.2 Add the frontend dependencies for React, TypeScript, `shadcn/ui`, routing, data fetching, `vitest`, `@testing-library/react`, and `fast-check`
- [x] 1.3 Configure local frontend runtime settings with a documented default backend origin and environment override support
- [x] 1.4 Add a local development workflow so the frontend can run against the Spring Boot backend on `http://localhost:8080`

## 2. Core Implementation

- [x] 2.1 Implement the application shell with navigation, connection-state banner, and UK retail-oriented visual framing
- [x] 2.2 Implement the frontend discovery service that resolves the backend origin and loads `/api-docs`
- [x] 2.3 Implement the OpenAPI endpoint catalogue builder and operator-facing grouping rules
- [x] 2.4 Implement the resource client for the current document-set, schema, derivative, validation, and content-related backend operations
- [x] 2.5 Implement the dashboard and API explorer views from discovered backend metadata
- [x] 2.6 Implement document-set list and detail views with pagination support
- [x] 2.7 Implement schema list, schema detail, and schema-version inspection views
- [x] 2.8 Implement action workflows for create schema, add schema version, create document set, add document, add version, create derivative, and validate document
- [x] 2.9 Implement the payload adapter for request preview, Base64 encoding, and response debugging panels
- [x] 2.10 Implement degraded-mode behaviour that disables unavailable backend-dependent actions while keeping independent UI surfaces accessible

## 3. Example-Driven Tests

- [x] 3.1 Add unit and component tests for root-route rendering, workflow navigation, and connection-state banners
- [x] 3.2 Add unit and component tests for error rendering, retry behaviour, and degraded discovery guidance
- [x] 3.3 Add unit and component tests for document-set and schema workflow happy paths against representative mocked backend responses

## 4. Property-Based Tests

- [x] 4.1 Implement property: Supported message types remain selectable and visible with `fast-check`
  - Generator: supported message-type enum values and workflow states that accept message framing
  - Test: every supported message type remains preserved in workflow state and available in presentation
  - **Validates: Requirements 1.3, 4.2**

- [x] 4.2 Implement property: Rendered resource views reflect backend payloads with `fast-check`
  - Generator: normalized list and detail payload objects with identifiers, metadata, and optional nested items
  - Test: rendered view models expose the same identifiers and core fields returned by the backend payloads
  - **Validates: Requirements 2.1, 2.2, 3.3, 4.3, 5.2, 5.3**

- [x] 4.3 Implement property: Pagination preserves continuation semantics with `fast-check`
  - Generator: paginated response objects with optional continuation tokens
  - Test: follow-up requests reuse the returned continuation token exactly
  - **Validates: Requirements 2.3**

- [x] 4.4 Implement property: Payload preview encoding is a round trip with `fast-check`
  - Generator: valid operator-entered text including multi-line EDI payloads and separator-heavy samples
  - Test: encoding then decoding yields the original payload text
  - **Validates: Requirements 3.2**

- [x] 4.5 Implement property: Failed submissions preserve operator inputs with `fast-check`
  - Generator: valid action-form inputs and failed mutation results
  - Test: failed workflow state retains the same operator input values after submission failure
  - **Validates: Requirements 3.4**

- [x] 4.6 Implement property: Configured origin resolution is deterministic with `fast-check`
  - Generator: combinations of explicit backend origins, default origins, and manual-state inputs
  - Test: explicit origin wins whenever present
  - **Validates: Requirements 6.3, 9.2**

- [x] 4.7 Implement property: Endpoint catalogue entries are contract-derived with `fast-check`
  - Generator: simplified OpenAPI path maps with tagged GET and POST operations
  - Test: every catalogue entry maps back to a source operation and belongs to exactly one operator-facing group
  - **Validates: Requirements 6.2, 7.1, 7.2**

- [x] 4.8 Implement property: Unavailable or invalid contract operations are rejected safely with `fast-check`
  - Generator: expected-operation sets plus OpenAPI documents that omit subsets or violate structural assumptions
  - Test: unavailable operations do not produce actionable endpoint bindings and invalid contracts yield unavailable action states
  - **Validates: Requirements 7.4, 9.4**

- [x] 4.9 Implement property: Degraded mode only disables dependent surfaces with `fast-check`
  - Generator: view registries annotated with backend dependency flags and degraded capability maps
  - Test: independent surfaces stay available and backend-dependent unavailable surfaces are disabled
  - **Validates: Requirements 8.4**

## 5. Review and Finish

- [x] 5.1 Conduct code review for the frontend change set
- [x] 5.2 Fix review findings and stabilise the UI copy, empty states, and developer-facing integration guidance
- [ ] 5.3 Run the frontend test suite, verify local backend integration manually, and document the startup steps
