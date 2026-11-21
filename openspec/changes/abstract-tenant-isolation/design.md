## Overview

Implement the seven findings in the [audit](../../../docs/architecture/multi-tenancy-abstraction-audit.md) as ordered migrations. Business use cases operate through scoped ports without selecting tenants. Trusted ingress adapters establish context; infrastructure resolves it per operation and rejects missing context or foreign-owned writes before side effects.

The first increment adds structural enforcement, not the runtime migration. Existing business authorization remains explicit. Tenant IDs remain legitimate ownership metadata in persisted records and durable envelopes.

## Architecture

```text
Authenticated HTTP / authenticated internal request / trusted work record
    -> ingress adapter binds tenant for one execution
    -> ordinary business service uses scoped domain ports
    -> infrastructure adapter resolves current tenant and checks ownership
    -> tenant-partitioned persistence / encrypted object storage
```

Same-thread module calls preserve context. Cross-thread and durable boundaries use explicit propagation adapters. Global AS2 resolution, cross-tenant work discovery, provisioning, and intentionally shared reference catalogs have separate capabilities; they must not be exposed to ordinary feature services.

## Components and Interfaces

### Architecture enforcement delivered first — MT 01 and MT 06

Use the existing `tooling/architecture-tests` module and backend CI job. Import the five domain packages, exclude test bytecode from production checks, and verify each domain/application package is present. No dependency from a domain onto the architecture module is introduced.

ArchUnit conditions reject active context dependencies, raw AWS clients, concrete infrastructure dependencies, explicit tenant selectors on repository/store ports, calls/references to tenant-selector operations, and business dependencies on ports exposing privileged discovery. Call-site checks include resolved inherited members and method references. String-input port methods require exact signature classification (`tenant`, `global`, `business`) with a review reason; parameter names are not used. Typed tenant-selector inputs cannot be exempted by classifying them as business strings.

Store migration exceptions as exact rule/member keys with audit finding and reason. Compare actual violations with the reviewed baseline in both directions. New violations and obsolete baseline entries fail; removed classified methods also fail. No test writes or updates policy files. Initial exceptions cover only existing source debt; no wildcard exceptions or automatic acceptance are supported.

The scope is structural. A renamed or arbitrary new operation's business semantics cannot be inferred from bytecode. Code review and behavioral contracts must still identify tenant selectors hidden in commands or ambiguous business strings. New privileged capabilities must live behind infrastructure boundaries, where existing dependency rules prevent business use.

### Scoped ports and ownership creation — MT 01

Domain owners retain domain-specific interfaces. Remove tenant-selector arguments from ordinary repository and storage operations, and enforce current-context scoping in infrastructure adapters on every invocation. Keep explicit-tenant database/object primitives infrastructure-internal. Reject existing record ownership that differs from current context; never silently rewrite a foreign record's owner.

Introduce narrow domain-owned creation ports alongside persistence ports so services can create records without reading static context or supplying tenant IDs. Their infrastructure implementations resolve context and call existing pure model factories with explicit ownership. Preserve model ownership fields and framework-independent domain constructors; do not add context calls or a shared tenant-aware aggregate superclass to models.

Migrate Exchange, Partnerships/SSCC, Transformations, then MFT. During the Exchange migration, scoped wrappers may temporarily delegate to existing explicit-tenant primitives in the other domains. Do not create new business-layer violations as a migration shortcut. Existing global catalog reads remain distinct from tenant mutation.

Before MFT service migration, split `MftRepository` into ordinary scoped operations, an infrastructure AS2 ownership resolver, and infrastructure pending-transfer/MDN discovery capabilities. Lease and status-conditional updates remain atomic. EventBridge/SQS producers, internal-token callers, and work-discovery adapters remain trusted infrastructure principals with explicit tenant authority.

### Tenant execution — MT 03 and MT 04

Keep `TenantContext` as the platform mechanism initially; replacing `ThreadLocal` is unnecessary to meet this contract. Provide a per-item trusted execution adapter with lexical cleanup and an executor task decorator that captures/restores context for configured handoffs. Do not use inheritable thread locals. Scope worker processing, lease follow-up, retries, errors, and MDC cleanup consistently per discovered record.

Parse and validate the JWT tenant before invoking downstream code in the active unified filter. Downstream validation exceptions follow normal application error translation rather than becoming a claim-validation 401. Remove the unregistered legacy filters after migrating their useful tests. Keep one active security chain, organization role rules, and separately authenticated internal/AS2 ingress.

### Documents storage and persistence — MT 02 and MT 05

Preserve the tenant-free `ContentStore` port and enforce scope inside its implementations. Resolve physical object identity as tenant plus content hash. Deployment storage must encrypt using tenant-bound authenticated context, reject foreign references, and fail startup if no supported backend is configured. Local/demo storage must enforce the same ownership contract with disposable encryption material. Shared bundled schema resources use a separate read-only reference-data capability. Keep metadata ownership checks before serving downloads.

Add a tenant-keyed schema-listing GSI and follow all pages. Deploy/backfill that index before switching list reads. Remove default-tenant persistence constructors from runtime usage and restrict explicit-tenant primitives to infrastructure. Validate decoded continuation keys against the active tenant before data access; keep existing query predicates and public pagination response types.

## Data Models and Compatibility

- Public HTTP/SDK shapes remain unchanged. Internal repository/storage/creation capabilities change deliberately; update their adapters, seeds, and tests in the same migration slice.
- Existing item ownership and tenant partition keys remain authoritative. Shared catalogs are not tenant data and cannot become tenant-writable.
- Inventory legacy hash-only content against scoped metadata. Copy each blob to every referencing tenant's encrypted namespace and verify its hash. Missing/corrupt source blobs block cutover for affected records. Identical bytes in two tenants become independently owned objects.
- During transition, writes use the new namespace; any legacy reader requires established tenant ownership from metadata. Record migration receipts without payload contents. Keep old blobs until a verified cutover and explicit retention/rollback decision; never delete them automatically as part of deployment.
- Backfill schema index attributes for all retained schema metadata before enabling indexed reads. Cursor hardening may require clients to restart listing after the existing invalid-token error.
- Local reset may rebuild disposable fixtures, but it is not a production migration strategy.

## Correctness Properties

### Property 1: New architectural violations fail

For any actual rule/member key absent from the reviewed baseline, enforcement fails; for any baseline key absent from actual violations, enforcement demands removal. **Validates: Requirements 1.1–1.4.**

### Property 2: Operations remain confined to their execution tenant

For any two distinct tenants using identical entity IDs, hashes, and idempotency keys, scoped reads, writes, deletes, and storage operations affect only the active tenant. Missing context or foreign ownership is rejected before side effects. **Validates: Requirements 2.1–2.3 and 3.1–3.3.**

### Property 3: Tenant lifetime does not outlive an execution

For any success, nested execution, exception, retry, or thread reuse, prior tenant/MDC state is restored; an unrelated task does not inherit a previous tenant. **Validates: Requirements 4.1–4.3.**

### Property 4: Tenant listings are complete and scoped

For any supported page size and mixed-tenant dataset, listing visits only the active tenant's records and eventually returns all its eligible records; foreign continuation keys fail before database access. **Validates: Requirement 5.**

## Error Handling

Unknown architectural classifications fail with the exact method signature. Baseline failures distinguish new violations from resolved exceptions. Runtime missing-context and foreign-ownership failures must occur before I/O and use existing HTTP translation where applicable. Claim-validation 401s are reserved for authentication failures. Migration failures retain old data and block cutover rather than falling back to unscoped/plaintext storage.

## Testing Strategy

JUnit and ArchUnit fixtures verify context reads/rebinding, raw clients, concrete persistence, tenant selectors, privileged declarations/dependencies, permitted ingress, ownership fields, business-key classifications, and baseline growth/cleanup/duplicate/provenance checks. Production imports must include every domain. Run existing pagination rules alongside tenancy rules.

Before each runtime migration, add shared two-tenant behavioral contracts and run them against durable and local/export adapters. Include create/read/update/delete, foreign-record saves, reused IDs, same-hash deletion, idempotency/replay, tenant predicates, foreign cursors, nested/error/thread-reuse context, worker leases, and retries. Use JUnit/Mockito at external boundaries and existing embedded database fixtures; no TestContainers.

For Properties 2–4, use the repository's jqwik support in the owning domain: generate distinct tenant IDs, shared entity IDs/keys, alternating execution outcomes, and varied page sizes; include at least 100 trials per property. Preserve failures as reproducible regression fixtures. Architecture fixture checks are deterministic and need no synthetic property generator.

Run focused architecture checks in a scratch copy with denied external networking and bounded execution, then the existing backend CI reactor. Runtime remediation acceptance additionally requires synthetic two-tenant preproduction verification and owner review of actual producer IAM, JWT issuer/audience configuration, and privileged internal access. These deployment gates cannot be inferred from mocked tests.

## Rollout and Observability

Land the executable guardrails first, followed by scoped-port domain slices. Reduce baseline entries and remove obsolete classification rows as each slice passes its contracts. Prepare encrypted content and schema-index migrations before read cutovers. Observe missing-context failures, ownership rejections, tenant/event correlation, retries, and migration verification outcomes without recording sensitive payloads. Publish each slice's verification evidence; do not mark the runtime migration complete while its baseline exceptions or behavioral gates remain open.
