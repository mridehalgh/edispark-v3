# Tenant isolation remediation tasks

Runtime remediation and executable architecture safeguards are implemented. Unchecked items identify verification or deployment gates still requiring external execution/review. Each group lists acceptance tests before implementation. Audit references refer to [MT 01–07](../../../docs/architecture/multi-tenancy-abstraction-audit.md).

## 1. Architecture guardrails — platform owner — MT 01 and MT 06

- [x] 1.1 Add deterministic ArchUnit fixture tests for prohibited context reads/rebinding, AWS clients, concrete infrastructure, typed/string selectors, global discovery, and allowed scoped services/ingress/ownership data. Verify new/resolved/duplicate/unprovenanced baseline behavior. **Validates: Requirement 1.**
- [x] 1.2 Implement production-domain imports, missing-domain/package checks, exact string/global classifications, and a read-only reviewed initial violation baseline with audit reasons.
- [x] 1.3 Run the complete architecture suite in the isolated scratch runner, including existing pagination checks; record toolchain and test results. Confirm the existing backend CI job includes the module without a new workflow. See [verification](verification.md).
- [x] 1.4 Document rule scope, structural limitations, policy review, and removal of migration exceptions. Link the audit to this remediation specification.

## 2. Scoped ports and creation — domain owners with platform support — MT 01

Depends on group 1. Migrate each domain's tests, adapters, local fixtures, and baseline entries together; do not add replacement business violations.

- [x] 2.1 Add a reusable behavioral contract covering absent context, same entity IDs in two tenants, foreign-owned saves, read/list/update/delete isolation, and idempotency. Run it against the first durable and test adapters before changing their ports. **Validates: Requirement 2; Property 2.**
- [x] 2.2 Migrate Exchange: remove repository tenant selectors, add context-scoped persistence and ownership creation adapters, and remove application context lookups. Preserve exchange/idempotency atomicity and existing HTTP behavior. Remove the corresponding baseline/classification entries.
- [x] 2.3 Add Partnerships/SSCC contract cases for foreign agreement/profile/issuer IDs, reservation concurrency, and shared idempotency keys; then migrate their scoped/creation ports and remove resolved exceptions.
- [x] 2.4 Add Transformations contract cases for foreign mapping IDs, shared catalog reads, and rejected shared/foreign mutations; then migrate scoped/creation ports and remove resolved exceptions.
- [x] 2.5 Add MFT capability-boundary tests for global AS2 ownership, work discovery, duplicate ownership claims, leases, and status-conditional writes. Split global discovery/AS2 resolution into infrastructure capabilities before migrating ordinary services.
- [x] 2.6 Migrate MFT repository, payload, secret, and creation capabilities; retain existing prefix/encryption guards, conditional transitions, and tenant-scoped replay/idempotency behavior. Remove all migrated MT 01 baseline entries and stale classifications.
- [x] 2.7 Run the full affected domain/application regression suites and SDK contract-diff checks. Record that internal API changes preserve public request/response shapes, or propose any necessary public contract change separately.

## 3. Documents content isolation and migration — Documents owner — MT 02

Can prepare independently after group 1; cutover depends on verified migration and runtime contracts.

- [x] 3.1 Add direct `ContentStore` and metadata-authorized download contracts: absent context, foreign hash/reference access, identical bytes in two tenants, independent deletion, tampered ciphertext, and invalid production backend configuration. **Validates: Requirement 3; Property 2.**
- [x] 3.2 Implement scoped local encrypted content storage and supported tenant-encrypted deployment storage; separate bundled reference-data reads. Remove the plaintext deployment fallback and fail unsupported startup configuration.
- [x] 3.3 Build a dry-run legacy ownership inventory and resumable copy/verification tool. Include unreferenced, multiply referenced, missing, and corrupt blobs; emit bounded migration receipts without content or secrets.
- [x] 3.4 Rehearse copying verified content into each owning tenant's namespace with synthetic data. Prove any temporary legacy reader requires scoped metadata authorization; retain legacy blobs for rollback.
- [ ] 3.5 Update deterministic local seeds/reset/coverage records and verify Documents workflows end to end against the new local backend. Do not use local reset as the deployment migration.
- [ ] 3.6 Gate deployment cutover on verified copy coverage, key/backend readiness, and a rollback rehearsal; document retention/deletion as a separate explicit operational action.

## 4. Trusted execution and HTTP lifetime — platform owner — MT 03 and MT 04

Depends on the scoped capability contract from group 2; implement worker changes alongside the MFT capability split.

- [x] 4.1 Add context behavior tests for nesting, exceptions, missing context, MDC restoration, thread reuse, executor handoffs, per-item retries, and alternating tenants. Add active-filter tests for missing/blank/malformed claims, spoofed headers, downstream business errors, and one-chain wiring. **Validates: Requirement 4; Property 3.**
- [x] 4.2 Provide trusted per-item execution and executor propagation adapters; migrate transfer, MDN, and inbound-event adapters without moving rebinding into ordinary business services. Preserve lease recovery and durable event tenant metadata.
- [x] 4.3 Parse JWT tenant claims outside downstream invocation in the unified filter. Port useful legacy tests, remove unregistered filters, and retain organization authorization/internal-token/AS2 trust boundaries.
- [ ] 4.4 Run worker retry/recovery, internal ingress, HTTP authorization, and two-tenant workflow checks. Confirm unauthorized event producers and internal callers remain outside the accepted trust boundary through deployment-owner review.

## 5. Documents persistence and cursors — Documents owner — MT 05

- [x] 5.1 Add mixed-tenant multi-page schema-listing tests, absent-context construction tests, same-ID CRUD cases, and foreign/malformed continuation-key rejection before I/O. **Validates: Requirement 5; Property 4.**
- [x] 5.2 Remove default-tenant runtime construction; keep explicit tenant primitives infrastructure-internal. Update tests to use explicit fixture scope rather than a silent default.
- [x] 5.3 Add the tenant-keyed schema listing index to deployment and local provisioning; backfill existing schema metadata and verify completeness before switching list reads to paginated queries.
- [x] 5.4 Validate cursor tenant/key shape before DynamoDB access and preserve query scoping and response compatibility. Verify existing invalid-token translation supports restarting a list.

## 6. Adapter parity, guidance, and completion — all owners — MT 06 and MT 07

Depends on groups 2–5.

- [x] 6.1 Add jqwik properties for scoped operations, context restoration, and paginated completeness (minimum 100 trials each), including shared IDs/keys/hashes and alternating failures. **Validates: Requirements 2–6; Properties 2–4.**
- [x] 6.2 Align export/test adapter keys with tenant plus entity identity and run the same isolation contracts against durable and disposable implementations. Verify scoped bean selection for local, deployment, and export profiles.
- [x] 6.3 Update tenancy rules/examples to match implemented context mechanics and scoped business APIs. Provide one tenant-free business use case and a separate trusted worker example. Keep explicit shared-catalog semantics.
- [x] 6.4 Remove all resolved baseline entries and classifications. Confirm migrated ordinary business services have no tenant selection/plumbing and no access to privileged discovery capabilities.
- [ ] 6.5 Run backend CI, local workflow checks, and synthetic two-tenant preproduction acceptance. Review actual IAM producer grants, internal endpoint/token custody, JWT settings, storage keys, and migration verification evidence. Record remaining deployment gates explicitly; do not infer them from mocked tests.
- [x] 6.6 Reconcile audit findings, specification status, and release evidence. Mark runtime completion only after contracts, migrations, and isolation checks pass; keep observational metrics free of payloads and secrets.

Runtime code and synthetic migration tests are delivered. Items 3.5, 4.4 and 6.5 retain their live/demo/deployment review requirements; 3.6 remains an operational cutover gate. Index provisioning/backfill tooling in 5.3 is implemented, but applying it to retained environments is part of 3.6. No production migration or rollback has been executed.
