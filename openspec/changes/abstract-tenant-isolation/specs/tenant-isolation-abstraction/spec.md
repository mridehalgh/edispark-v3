## Introduction

Feature developers build business workflows through scoped ports. Platform infrastructure owns tenant authority, lifetime, and resource isolation. Structural architecture checks prevent new tenant plumbing while an explicit migration baseline tracks existing debt.

## Glossary

- **Scoped Port**: A domain repository, storage, or creation capability that resolves the active execution tenant without a caller-provided tenant selector.
- **Trusted Boundary**: Authenticated ingress, an authorized durable-message consumer, or infrastructure work discovery allowed to establish tenant execution context.
- **Baseline**: Exact reviewed architectural rule/member exceptions with audit provenance and migration reasons.

## ADDED Requirements

### Requirement: 1. Executable tenant boundary enforcement

The architecture suite SHALL enforce the tenant boundaries and reviewed-baseline constraints below in backend CI.

**User Story:** As a platform maintainer, I want CI to reject new tenant plumbing, so that isolation responsibilities cannot spread during migration.

#### Acceptance Criteria

1. THE architecture suite SHALL check every domain's production business/application classes and fail when expected modules or packages are missing.
2. THE suite SHALL reject context access, raw AWS client dependencies, concrete infrastructure dependencies, tenant-selector repository/store methods, business calls or references to tenant-selector operations, and business dependencies on classified global discovery ports.
3. THE suite SHALL identify typed tenant selectors and require exact reviewed classifications for string-input port signatures without using parameter names.
4. THE suite SHALL reject new violations, duplicate or unprovenanced exceptions, stale baseline entries, and removed classified signatures; tests SHALL NOT update policy resources.
5. THE suite SHALL permit trusted ingress adapters, tenant ownership metadata, scoped services, justified business-string inputs, and shared reference catalogs.

#### Scenario: A new feature selects tenant context

- **WHEN** a business service adds a context lookup absent from the baseline
- **THEN** CI fails with the responsible rule and member signature.

#### Scenario: A migrated feature no longer violates a rule

- **WHEN** an existing violation disappears
- **THEN** CI requires its baseline entry to be removed rather than retaining a dormant exception.

### Requirement: 2. Scoped business capabilities

Ordinary business capabilities SHALL resolve tenant authority through scoped adapters rather than developer-supplied tenant selectors.

**User Story:** As a feature developer, I want business identifiers and scoped ports to be sufficient, so that I do not implement tenant filtering or ownership assignment.

#### Acceptance Criteria

1. THE migrated application services SHALL use tenant-free repository, storage, and creation capabilities for ordinary business operations.
2. THE infrastructure adapters SHALL resolve current context per operation and reject missing context or mismatched record ownership before side effects.
3. THE system SHALL preserve tenant-scoped entity identity, idempotency, conditional writes, and lease semantics across adapters.
4. THE system SHALL expose privileged global discovery and public shared reference data through separate capabilities rather than ordinary tenant repositories.

#### Scenario: Foreign-owned record save

- **WHEN** tenant A attempts to save a record owned by tenant B through a scoped port
- **THEN** the adapter rejects it before persistence and does not relabel the record.

### Requirement: 3. Tenant-isolated document content

Document content storage SHALL enforce independent tenant ownership and encryption without relying solely on caller-side metadata checks.

**User Story:** As a tenant, I want content isolation enforced by storage, so that knowing another tenant's content hash cannot grant access or permit deletion.

#### Acceptance Criteria

1. THE content implementations SHALL scope store, retrieve, exists, and delete by current tenant and content hash.
2. THE deployment backend SHALL encrypt content with tenant-bound authenticated context and SHALL fail startup when no supported backend is configured.
3. THE local backend SHALL enforce equivalent ownership and deletion isolation with disposable local encryption; public bundled reference resources SHALL use a distinct read-only capability.
4. THE migration SHALL map legacy blobs to their referencing tenants, verify copied content, retain rollback data, and prevent unscoped legacy fallback.

#### Scenario: Shared bytes with independent ownership

- **WHEN** tenants A and B independently retain identical bytes and tenant A deletes its object
- **THEN** tenant B can still retrieve its own verified content.

### Requirement: 4. Trusted execution lifetime

Trusted ingress and worker adapters SHALL bind and restore tenant execution context for each operation and failure path.

**User Story:** As a platform maintainer, I want ingress and workers to bind context consistently, so that failures and reused threads cannot carry another tenant's authority.

#### Acceptance Criteria

1. THE trusted adapters SHALL establish context from authenticated claims, authenticated internal requests, authorized messages, or trusted discovered records and restore prior tenant/MDC state on every outcome.
2. THE worker execution facility SHALL scope each item and retry independently; configured executor handoffs SHALL explicitly capture and restore context.
3. THE active HTTP filter SHALL validate claims before downstream execution and SHALL NOT translate downstream business-validation failures into tenant-claim errors.
4. THE unified application SHALL retain one active security chain and separate role/permission enforcement.

#### Scenario: Failed tenant A work followed by tenant B work

- **WHEN** tenant A processing throws and the same worker thread later processes tenant B
- **THEN** tenant B executes solely with its own context and cleanup restores the thread's prior state.

### Requirement: 5. Scoped and complete persistence queries

Tenant repositories SHALL reject implicit default tenancy and foreign cursors and SHALL return complete tenant-scoped listings.

**User Story:** As a tenant, I want listings and pagination to remain confined and complete, so that other tenants cannot affect which records I receive.

#### Acceptance Criteria

1. THE runtime repositories SHALL NOT silently select a default tenant.
2. THE schema listing SHALL use a tenant-keyed index and follow retained pages until complete.
3. THE pagination adapter SHALL reject continuation keys belonging to another tenant before data access while retaining tenant predicates.
4. THE index migration SHALL deploy and backfill required attributes before switching reads; public pagination response shapes SHALL remain compatible.

#### Scenario: Foreign continuation token

- **WHEN** tenant A submits a token encoding tenant B's key
- **THEN** the request returns the existing invalid-token error before issuing a database query.

### Requirement: 6. Migration evidence and developer guidance

The migration SHALL demonstrate equivalent isolation across runtime adapters and SHALL align developer guidance with implemented boundaries.

**User Story:** As a feature developer, I want examples and local adapters to use the supported abstraction, so that working locally teaches the same isolation contract as deployment.

#### Acceptance Criteria

1. THE domain migrations SHALL pass shared two-tenant contracts across durable and local/export adapters, including identical IDs, keys, hashes, foreign writes, and failure paths.
2. THE developer guidance SHALL accurately describe the implemented execution model and separate trusted-boundary responsibilities from business code.
3. THE migration SHALL remove its architectural exceptions as responsibilities move behind scoped adapters and SHALL report runtime/deployment checks separately from structural checks.

#### Scenario: Same entity ID in two adapters

- **WHEN** two tenants reuse the same entity ID in either a durable or test adapter
- **THEN** each tenant observes only its own record, and modifying one does not overwrite the other.
