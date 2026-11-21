## Glossary

- **EDI MCP Server** is the remote EdiSpark tool endpoint.
- **Pin** identifies an exact persisted revision or content representation.

## ADDED Requirements

### Requirement: Delegated access

THE EDI MCP Server SHALL restrict each tool call to an approved client and workspace.

**User Story:** As a workspace owner, I want delegated access scoped to the workspace and client so that agent access follows my authorization.

#### Acceptance Criteria

1. WHERE the pilot is enabled, THE EDI MCP Server SHALL require a valid resource-bound token and current approved client and workspace access.
2. IF a token, scope, workspace, or client is unauthorized, THEN THE EDI MCP Server SHALL deny access.

#### Scenario: Deny a revoked grant

- **GIVEN** the workspace owner approved a client and the client holds a resource-bound token
- **WHEN** the owner removes the client's consent
- **THEN** the EDI MCP Server SHALL deny the client's subsequent request

### Requirement: Evidence and previews

THE EDI MCP Server SHALL provide persisted evidence and deterministic previews.

**User Story:** As an operator, I want to investigate and test against verifiable evidence so that I can fix a failed exchange.

#### Acceptance Criteria

1. THE EDI MCP Server SHALL expose bounded exchange searches, persisted traces, route evidence, mapping previews, and outbound readiness checks.
2. WHEN a caller submits a stale required pin, THE EDI MCP Server SHALL reject the preview.
3. THE EDI MCP Server SHALL exclude raw retained business payloads and credentials from tool results.
4. THE EDI MCP Server SHALL leave business state unchanged during searches, traces, and previews.

#### Scenario: Recheck a changed document

- **GIVEN** a client holds a pin to an earlier representation of a document
- **WHEN** the client checks outbound readiness with that pin
- **THEN** the EDI MCP Server SHALL return a stale-source error without changing business state

### Requirement: Workspace AI guide

THE EdiSpark workspace SHALL provide organisation admins and owners with an AI showcase, client setup instructions, and a reference for every MCP tool.

#### Scenario: Show workspace connection details

- **GIVEN** an organisation administrator opens the AI guide
- **WHEN** the active organisation is enrolled and the MCP endpoint is configured
- **THEN** the guide SHALL display the endpoint for copying

#### Scenario: Show the guide before enrolment

- **GIVEN** an organisation administrator opens the AI guide
- **WHEN** the active organisation has no eligible MCP setup
- **THEN** the guide SHALL display setup guidance without an endpoint

### Requirement: Document workflow and transport evidence

THE EDI MCP Server SHALL support bounded tenant-scoped document search, recorded document links, qualified route lookup, and separately authorised AS2 evidence.

#### Scenario: Follow a business document through the recorded workflow

- **GIVEN** an approved client has document evidence access and knows an exact purchase order reference
- **WHEN** the client finds the order and follows its document workflow
- **THEN** the EDI MCP Server SHALL return recorded links and current lifecycle states with continuation where applicable

#### Scenario: Reuse the order response authoring flow

- **GIVEN** an approved client has preview access for a retained order
- **WHEN** the client prepares an order response
- **THEN** the EDI MCP Server SHALL return a pinned link to the existing smart-copy authoring form
- **AND** the author SHALL review copied commercial values before saving

#### Scenario: Inspect AS2 transport evidence

- **GIVEN** an approved client has AS2 evidence access
- **WHEN** the client traces a transfer
- **THEN** the EDI MCP Server SHALL report the recorded attempts, retry timing and receipt evidence separately

#### Scenario: Reconcile without exposing commercial values

- **GIVEN** an approved client has document evidence access without the commercial scope
- **WHEN** the client compares exact retained representation pins
- **THEN** the EDI MCP Server SHALL return structured findings without amounts or quantities
