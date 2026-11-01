# Trading network delta

## ADDED Requirements

### Requirement: Tenant-isolated trading partners

The system SHALL model a tenant-owned business counterparty separately from its transport connections, SHALL derive tenant ownership from the authenticated token, SHALL prevent duplicate business identities within a tenant, and SHALL prevent cross-tenant access.

#### Scenario: Tenant creates a distinct business partner

- **WHEN** an authenticated integration analyst creates a retailer with a business identity
- **THEN** the system stores the partner in the authenticated tenant without coupling it to an AS2 transport profile

#### Scenario: Cross-tenant identifier is requested

- **WHEN** a caller requests a partner, agreement, mapping, or exchange owned by another tenant
- **THEN** the system does not expose that resource

### Requirement: Strict agreement qualification

The system SHALL require every capability to reference a matching published mapping, supported document/message pairing and release, and active same-tenant AS2 connection before activation. Active agreement revisions SHALL remain immutable.

#### Scenario: Complete agreement is activated

- **WHEN** all capability checks pass and the analyst activates the draft agreement
- **THEN** the system stores the qualification evidence and activates that exact revision

#### Scenario: Incomplete agreement is rejected

- **WHEN** any mapping, standard, schema, message, or AS2 check fails
- **THEN** the system retains the failures and refuses activation

#### Scenario: Active agreement needs a change

- **WHEN** an analyst revises an active agreement
- **THEN** the system creates a new draft revision instead of mutating the active revision

### Requirement: Safe visual mappings

The mapping runtime SHALL execute only a bounded declarative operation set, enforce node and byte limits, require a passing fixture before publication, and make published revisions immutable with a SHA-256 content hash.

#### Scenario: Proven mapping is published

- **WHEN** a draft mapping passes its supplied input/output fixture
- **THEN** the system publishes an immutable content-addressed mapping revision

#### Scenario: Unbounded or unproven mapping is submitted

- **WHEN** a mapping exceeds a configured limit or fails its fixture
- **THEN** the system rejects execution or publication without running arbitrary code

### Requirement: Idempotent document exchange

The system SHALL pin the source document version and hash, retain generated EDI as a variant-keyed derivative, submit MFT with complete provenance, and expose ordered lifecycle evidence and explicit outcomes.

#### Scenario: Qualified document is sent

- **WHEN** a caller sends a document through an active qualified capability
- **THEN** the system transforms it, retains the generated EDI derivative, submits it to AS2, and records document-to-transfer provenance

#### Scenario: Send request is retried

- **WHEN** the same tenant idempotency key is submitted more than once
- **THEN** the system returns the original exchange without duplicating the transport submission

### Requirement: Composable API and operations UI

The system SHALL publish an OpenAPI contract and generated TypeScript client. Browser access SHALL use an authenticated server-side BFF, document views SHALL show only eligible routes, and the Trading Network UI SHALL expose configuration and exchange evidence.

#### Scenario: Analyst configures and operates the trading network

- **WHEN** an authenticated analyst opens the Trading Network
- **THEN** they can manage partners, agreement qualification, mappings, and inspect exchange evidence using the same API contracts available to external integrators

