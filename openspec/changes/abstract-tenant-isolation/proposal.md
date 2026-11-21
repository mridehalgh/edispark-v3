## Why

Feature developers currently select tenant IDs and pass them through repositories and storage operations. That makes isolation depend on repeated application code. The [audit](../../../docs/architecture/multi-tenancy-abstraction-audit.md) identifies seven gaps, including unscoped Documents content storage and inconsistent execution boundaries.

## What Changes

- Add executable cross-domain ArchUnit rules now, with an exact reviewed baseline that blocks new violations and requires removal of resolved exceptions.
- Move routine tenant selection into scoped persistence, storage, and record-creation adapters. Separate privileged global discovery from ordinary MFT capabilities.
- Add encrypted, tenant-scoped Documents content storage, a migration from existing blobs, indexed schema listing, and tenant-bound pagination validation.
- Standardize trusted HTTP/worker context lifetime and add shared isolation contracts across runtime profiles.
- Align developer guidance and test doubles with the implemented contract.

Runtime refactors, encrypted storage, migration tooling and architecture safeguards are delivered. Deployed-environment migration, rollback and authorization checks remain explicit release gates; see verification.md.

## Capabilities

### New Capabilities

- `tenant-isolation-abstraction`: developer-facing scoped ports, automated boundary enforcement, tenant-isolated storage and execution, and migration acceptance criteria.

### Modified Capabilities

None. This change defines a cross-cutting capability; it does not rewrite unrelated business workflow specifications.

## Impact

All five domains, shared tenancy infrastructure, the unified composition root, Documents storage/table provisioning, local/demo adapters, and developer guidance. No additional deployable service is introduced. Internal Java interfaces change during remediation; HTTP request/response shapes and generated SDK contracts remain stable unless a later explicit contract change is required. Content addressing and the schema-listing index require staged migration and rollout evidence.
