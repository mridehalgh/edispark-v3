# Multi tenancy abstraction audit

Reviewed source: `61e41f158ddad7b6e195337080d689093c7cbe50` (initially clean working tree).
Date: 2026-11-21. Audience: feature developers and platform maintainers.

## Runtime remediation status

The findings below describe the reviewed base commit, before remediation. The current working tree replaces manual selectors with scoped/creation ports across all five domains, separates privileged MFT discovery, scopes/encrypts Documents content, validates cursors, queries tenant schema indexes, and supplies trusted execution propagation. The ArchUnit violation baseline is empty. See [verification](../../openspec/changes/abstract-tenant-isolation/verification.md), [developer guidance](tenant-scoped-development.md), and [deployment migration gates](documents-storage-migration.md). Historical evidence paths refer to the base commit; source links now show remediated files.

## Historical assessment

**Multi-tenancy is partially abstracted from feature developers.** Authentication establishes request context centrally, and Documents hides tenant selection behind repository adapters. MFT, Partnerships, Transformations, and Exchange still require application services to obtain tenant IDs, put them on new records, and pass them into persistence or storage operations. Their adapters generally isolate the tenant supplied by the caller; they do not independently establish that it is the authenticated tenant.

The target should be that an ordinary business use case accepts business identifiers and commands, uses tenant-scoped ports, and cannot accidentally select a different tenant. Platform and infrastructure code should own trusted tenant resolution, context lifetime, storage namespaces, and propagation. Roles, permissions, partner qualification, and other business authorization remain explicit application responsibilities.

This is a focused architecture and source audit, not a certification of production isolation or a comprehensive security audit. No remotely exploitable cross-tenant access was demonstrated. Priorities below are engineering priorities, not vulnerability severities. The original audit added no runtime behavior. Subsequent remediation is recorded above.

## Boundary matrix

“Automatic” describes the inspected path, not every possible caller of its dependencies. “Manual” means a caller must correctly select or propagate a tenant. “Unverified” identifies a missing runtime assertion or deployment fact.

| Boundary | Current behavior and evidence | Assessment |
| --- | --- | --- |
| Organization to JWT | Token issuance checks active organization membership, resolves its tenant, and supplies `tenant_id` and `organization_role` in [auth.ts](../../apps/web/src/lib/auth.ts). | Automatic for this issuer; live identity configuration unverified. |
| Browser and BFF | [Documents](../../apps/web/src/lib/documents-server.ts), [MFT](../../apps/web/src/lib/mft-server.ts), and [Integration](../../apps/web/src/lib/integration-server.ts) client factories obtain a session token; feature calls need no tenant argument. | Substantially abstracted; separate factories still own access policies. |
| Unified HTTP API | `../../apps/edispark-api/src/main/java/com/edispark/wireup/security/UnifiedTenantContextFilter.java` (removed legacy source) binds the JWT tenant for `/api/` execution and rejects absent/blank/invalid claims. [SecurityConfiguration](../../apps/edispark-api/src/main/java/com/edispark/wireup/security/SecurityConfiguration.java) applies role rules separately. | Automatic at authenticated ingress. |
| Legacy HTTP filter classes | `../../apps/documents-api/src/main/java/com/edispark/application/security/TenantContextFilter.java` (removed legacy source), `../../apps/mft-api/src/main/java/com/edispark/mftapplication/security/MftTenantContextFilter.java` (removed legacy source), and `../../apps/integration-api/src/main/java/com/edispark/integrationapplication/security/IntegrationTenantContextFilter.java` (removed legacy source) retain separate implementations. No runtime registrations were found; these modules now provide adapters to the unified application rather than separate Boot entrypoints. | Duplicated legacy code; only the unified filter is installed in the inspected security chain. |
| Internal HTTP | [InternalTransferController](../../domains/mft/src/main/java/com/edispark/mft/api/rest/InternalTransferController.java) and [InternalInboundDocumentController](../../domains/documents/src/main/java/com/edispark/documents/api/rest/InternalInboundDocumentController.java) authenticate an internal token before binding an explicit tenant header. | Deliberately privileged ingress; possession of the service token conveys cross-tenant authority. Deployment exposure and token distribution unverified. |
| Context lifetime | [TenantContext](../../platform/tenancy/src/main/java/io/cottn/core/tenancy/TenantContext.java) uses `ThreadLocal`, throws when unset, and restores prior tenant/MDC values in a `finally`-equivalent scope. | Automatic within the current thread; no general executor propagation abstraction found in production Java searches. |
| Documents repositories | [Document sets](../../domains/documents/src/main/java/com/edispark/documents/infrastructure/persistence/TenantScopedDocumentSetRepository.java) and [schemas](../../domains/documents/src/main/java/com/edispark/documents/infrastructure/persistence/TenantScopedSchemaRepository.java) resolve context per operation. Domain ports have no tenant parameter. | Best existing example of the intended developer experience. |
| Documents persistence | [Document-set adapter](../../domains/documents/src/main/java/com/edispark/documents/infrastructure/persistence/DynamoDbDocumentSetRepository.java) scopes keys, list GSI, content-hash GSI, and deletes by constructor tenant. [Schema adapter](../../domains/documents/src/main/java/com/edispark/documents/infrastructure/persistence/DynamoDbSchemaRepository.java) scopes point operations but lists through a filtered scan. Both expose a default-tenant constructor. | Scoped through configured wrappers; direct construction remains an escape hatch. Schema listing also has completeness/fairness issues. |
| Document pagination | The list query retains its tenant key condition and reloads results through scoped lookups. [PaginationTokenCodec](../../domains/documents/src/main/java/com/edispark/documents/infrastructure/persistence/PaginationTokenCodec.java) decodes a caller-supplied DynamoDB key without tenant validation. | Query isolation visible in source; foreign-tenant cursor behavior unverified. A token alone does not establish a data-leak finding. |
| Documents content | [FileSystemContentStore](../../domains/documents/src/main/java/com/edispark/documents/infrastructure/storage/FileSystemContentStore.java) reads/writes/deletes by content hash under one shared directory. [DocumentContentQueryService](../../domains/documents/src/main/java/com/edispark/documents/application/query/DocumentContentQueryService.java) first resolves ownership through document metadata. | Ownership enforced by the inspected query service, not storage itself. Direct storage users must preserve that invariant. |
| MFT repositories and storage | [MftRepository](../../domains/mft/src/main/java/com/edispark/mft/domain/repository/MftRepository.java), [PayloadStore](../../domains/mft/src/main/java/com/edispark/mft/domain/repository/PayloadStore.java), and [SecretStore](../../domains/mft/src/main/java/com/edispark/mft/domain/repository/SecretStore.java) accept tenant IDs, or records containing them. The same repository also exposes global ingress/work discovery. | Manual tenant selection in feature services; tenant and privileged capabilities are mixed. |
| MFT object isolation | [S3 payloads](../../domains/mft/src/main/java/com/edispark/mft/infrastructure/storage/S3EncryptedPayloadStore.java) and [secrets](../../domains/mft/src/main/java/com/edispark/mft/infrastructure/storage/S3EncryptedSecretStore.java) reject foreign prefixes before S3 reads. [Tenant cipher](../../domains/mft/src/main/java/com/edispark/mft/infrastructure/storage/AwsHierarchicalKeyringTenantCipher.java) binds encryption context to tenant and service and resolves a branch key. | Central resource guards exist, but callers supply the tenant being guarded. |
| Other business domains | [ExchangeRepository](../../domains/exchange/src/main/java/com/edispark/exchange/domain/ExchangeRepository.java), [MappingRepository](../../domains/transformations/src/main/java/com/edispark/transformations/domain/MappingRepository.java), [PartnershipRepository](../../domains/partnerships/src/main/java/com/edispark/partnerships/domain/repository/PartnershipRepository.java), and [SsccRepository](../../domains/partnerships/src/main/java/com/edispark/partnerships/domain/repository/SsccRepository.java) require explicit tenant arguments. | Application developers repeatedly implement tenant plumbing. |
| Integration DynamoDB | [IntegrationDynamoStore](../../apps/integration-api/src/main/java/com/edispark/integrationapplication/persistence/IntegrationDynamoStore.java) scopes get/put/delete/list/transactions with `TENANT#…`; list follows all retained pages. [Exchange adapter](../../apps/integration-api/src/main/java/com/edispark/integrationapplication/persistence/DynamoExchangeRepository.java) scopes idempotency claims. | Storage mechanics centralized; tenant authority still comes from caller arguments or record fields. |
| In-process calls | [InProcessDocumentAcl](../../apps/edispark-api/src/main/java/com/edispark/wireup/modules/InProcessDocumentAcl.java) uses already-scoped repositories. [InProcessInboundModuleAccess](../../apps/edispark-api/src/main/java/com/edispark/wireup/modules/InProcessInboundModuleAccess.java) supplies event tenant IDs explicitly to MFT. | Same-thread context is retained; adapters use different tenancy conventions. |
| Background work | [Transfer worker](../../domains/mft/src/main/java/com/edispark/mft/infrastructure/as2/MftDeliveryWorker.java) binds a tenant per discovered record. [MDN worker](../../domains/mft/src/main/java/com/edispark/mft/infrastructure/as2/AsyncMdnDeliveryWorker.java) instead passes record tenant IDs directly. | Explicit handling is appropriate at discovery; no uniform per-item execution boundary. |
| Events and retry | [Inbound processor](../../apps/integration-api/src/main/java/com/edispark/integrationapplication/inbound/InboundMftEventProcessor.java) validates an event and binds its tenant. [SQS consumer](../../apps/integration-api/src/main/java/com/edispark/integrationapplication/inbound/SqsInboundMftEventConsumer.java) deletes only after success. [Terraform](../../infrastructure/terraform/integration/main.tf) constrains EventBridge queue delivery to the named rule. | Manual at ingress; actual IAM grants and authorized producers require deployment validation. Event source strings alone are not authentication. |
| Caches and shared catalog | [JSON validator](../../domains/documents/src/main/java/com/edispark/documents/infrastructure/validation/JsonSchemaDocumentValidator.java) caches bundled UBL schemas, not tenant-authored schemas. MFT keyrings are keyed by resolved branch ID. [MappingService](../../domains/transformations/src/main/java/com/edispark/transformations/application/MappingService.java) deliberately combines a global catalog with tenant mappings and uses tenant-only mutation paths. | Inspected shared data has an explicit purpose; do not blindly tenant-prefix global reference data. |
| Local and export adapters | [Unified local wiring](../../apps/edispark-api/src/main/java/com/edispark/wireup/modules/LocalDurableMetadataConfiguration.java) selects durable DynamoDB MFT metadata; Integration persistence uses `!openapi-export`. Export-only in-memory mapping/exchange/partnership stores commonly key entities by UUID alone. MFT module local config declares an in-memory repository, superseded by the primary durable bean in unified local wiring. | Profiles differ. Export/test doubles cannot substitute for production isolation evidence. |

## Findings and prioritized remediation

### MT 01 — P1 — Tenant selection remains a feature developer responsibility

[MftService](../../domains/mft/src/main/java/com/edispark/mft/application/MftService.java), [ExchangeService](../../domains/exchange/src/main/java/com/edispark/exchange/application/ExchangeService.java), [MappingService](../../domains/transformations/src/main/java/com/edispark/transformations/application/MappingService.java), and [PartnershipService](../../domains/partnerships/src/main/java/com/edispark/partnerships/application/PartnershipService.java) retrieve context themselves and pass tenant IDs into domain ports. New records also require tenant assignment. A developer can omit a lookup guard, use the wrong record's tenant, or inject an unscoped implementation without an architectural failure.

This is a confirmed abstraction gap. These inspected call sites do not by themselves demonstrate unauthorized access.

**Remediation:** platform maintainers define the shared context contract; domain owners expose tenant-scoped repository/storage ports with no tenant selector on ordinary operations. Infrastructure adapters resolve current context for every operation and reject writes whose ownership differs from that context before side effects. Supply tenant ownership at the application/infrastructure creation boundary; do not require pure business logic to call static context or inherit a framework aggregate. Keep explicit tenant-aware persistence primitives internal to infrastructure.

Split MFT global AS2 ownership resolution and pending-work discovery into separately injectable infrastructure capabilities before migrating its ordinary services. Preserve conditional writes, leases, pair ownership transactions, and idempotency behavior.

**Acceptance:** routine create/read/list/update/delete operations and payload access require no tenant arguments or context calls in feature services; absent context fails closed; foreign-owned records cannot be saved through scoped ports. Integration and Documents owners apply the same contract without creating a generic business repository superclass.

### MT 02 — P1 — Documents storage does not enforce a tenant boundary

The content store's namespace is only the hash. It has no context requirement, tenant namespace, or tenant encryption. The inspected content query service checks document ownership first, so this is not evidence of an HTTP download bypass. Direct calls to the storage port, including its `exists` and `delete` operations, cannot enforce tenant isolation themselves.

[DocumentsModuleConfig](../../domains/documents/src/main/java/com/edispark/documents/infrastructure/config/DocumentsModuleConfig.java) supplies this filesystem store locally and as its non-local fallback. Its S3 option throws `UnsupportedOperationException`. This gap affects the available production storage wiring as well as local behavior.

**Remediation:** Documents owners retain the convenient tenant-free `ContentStore` interface but implement context-scoped storage with per-tenant hash namespaces, scoped existence/deletion, and tenant-bound encryption for deployment. Production configuration must require a supported encrypted backend and fail startup rather than silently select plaintext filesystem storage. Local storage should enforce the same ownership contract with disposable local encryption. Use an explicit public-reference store for intentionally shared bundled schemas.

**Acceptance:** the same bytes can exist independently in two tenants; deleting one tenant's content leaves the other intact; a tenant cannot retrieve another tenant's blob by knowing its hash. Verify both metadata-authorized HTTP paths and direct storage calls. Missing context must fail before filesystem/S3 access.

**Compatibility:** this changes physical object addressing. Before rollout, inventory persisted hash-only blobs and their referencing tenants; copy each referenced blob into each owning tenant's namespace, verify hashes, then switch references/backend configuration. Any temporary legacy reader must establish ownership from scoped metadata. Retain old blobs until verification and rollback requirements are satisfied; do not assume local reset is an acceptable production migration.

### MT 03 — P2 — Context propagation is hand written at execution boundaries

`TenantContext` has useful lexical cleanup, including restoration of nested contexts and MDC in source. It uses a plain `ThreadLocal`; a new executor task does not automatically receive it. No general production executor decorator was found. The transfer worker and inbound processor bind manually; the asynchronous MDN worker deliberately uses explicit tenant arguments instead. This is inconsistent infrastructure, not proof that these current workers process the wrong tenant.

**Remediation:** after MT 01 establishes the contract, platform owners provide one per-item tenant execution facility for trusted ingress/work adapters and a context-capturing decorator for any introduced executor handoffs. Bind from authenticated claims, authenticated internal messages, or trusted discovered records. Bind/restore per item, including retry and error paths. Do not use inheritable thread-local state as a thread-pool propagation strategy. Keep tenant-bearing durable envelopes explicit; an implicit thread context cannot replace their ownership information.

**Acceptance:** alternating tenants on one reused thread, nested scopes, thrown exceptions, and retries leave no residual tenant/MDC state. Unscoped work fails rather than borrowing a prior tenant. Ordinary in-process module calls retain current context without rebinding.

### MT 04 — P2 — The HTTP tenant filter can misclassify business errors

The active unified filter catches `IllegalArgumentException` around both tenant parsing and downstream request execution. Consequently a business validation exception escaping downstream can be converted into a tenant-claim 401. Unregistered legacy MFT and Integration filters retain the same pattern; the legacy Documents filter parses outside the downstream execution scope. This is a source-visible error-handling risk, not an isolation bypass. Normal MVC exception handling may consume a business exception before it reaches the filter.

**Remediation:** keep the unified filter as the single Spring servlet adapter and keep the core tenancy module free of servlet dependencies. Parse authenticated tenant claims before invoking application code, restore context on all outcomes, and let downstream errors follow normal exception translation. Remove unregistered legacy filters and migrate their useful tests onto the active adapter. Preserve the unified security configuration's role rules and explicit internal/AS2 boundaries.

**Acceptance:** checks against the active filter and unified security chain cover missing/blank/malformed claims, spoofed tenant headers, success, downstream validation failures, and exceptions. A valid claim plus invalid business input must not become an authentication failure; the application retains one security filter chain.

### MT 05 — P2 — Persistence escape hatches and cursor behavior need explicit contracts

Documents' low-level repositories have constructors that silently use `DEFAULT_TENANT`. Production bean wiring uses the scoped wrappers, but the constructors permit new code to bypass context. Schema `findAll()` scans with a tenant filter and does not follow `LastEvaluatedKey`, so it can return an incomplete tenant list while consuming table-wide read work. The document cursor is decoded without comparing its tenant fields to current context, although query and reload operations remain scoped.

**Remediation:** Documents owners remove silent default-tenant construction from runtime adapters and make explicit-tenant construction infrastructure-only. Add a tenant-keyed schema listing index, backfill existing schema metadata, and follow its pages. Reject a foreign or malformed continuation key before DynamoDB use while keeping current-tenant query predicates. Preserve the existing public pagination response shape.

**Acceptance:** construction cannot silently select a tenant; mixed-tenant multi-page schema data returns all and only the current tenant's schemas; foreign cursors are rejected without data access; foreign IDs cannot read/delete records. Test IDs reused across tenants, not only random globally unique IDs.

**Compatibility:** deploy and backfill the schema index before switching reads. Cursor hardening may reject previously accepted tokens; clients must restart listing after that error. No item-key migration is required for removing constructors.

### MT 06 — P2 — Developer safeguards and adversarial coverage are uneven

The cross-domain [architecture suite](../../tooling/architecture-tests/src/test/java/com/edispark/architecture/PaginationArchitectureTest.java) enforces pagination conventions, not tenancy ownership. The current Java context test covers basic binding and an empty MDC afterward; it does not assert missing-context failure after cleanup or cover nested/error/thread-reuse behavior. Documents repository integration fixtures exercise ordinary persistence through default-tenant construction. Existing MFT tests provide more explicit tenant attack scenarios.

**Remediation:** add scoped-port contract tests shared across durable and test adapters. Introduce ArchUnit rules prohibiting tenant rebinding, raw storage clients, and privileged discovery dependencies in ordinary business services. During migration, maintain a named, shrinking allowlist of existing exceptions; after MT 01, forbid context plumbing outside designated infrastructure/application-boundary packages. Add a short developer example demonstrating a tenant-free business use case and a separate worker adapter.

**Acceptance:** adding a service that selects arbitrary tenants or injects global work discovery fails CI. Each runtime profile is checked for the correct scoped bean bindings. A new feature's isolation is verified by shared contracts rather than requiring its author to invent tenant filtering.

### MT 07 — P3 — Guidance and test doubles can reinforce obsolete patterns

[Multi-tenancy rules](../../.agents/rules/multi-tenancy-architecture.md) describe `ScopedValue` and demonstrate tenant-aware feature services, whereas the implemented context is `ThreadLocal`. Generic `TenantAwareAggregateRoot` and event metadata helpers exist but no production usages were found in the searched domain/application Java files. Export-only entity maps keyed solely by UUID can overwrite another tenant's fixture record when IDs are reused; this is not the unified durable local deployment path.

**Remediation:** after the chosen contract is implemented, update rules and developer examples to match it. Describe the current execution model accurately rather than prescribing a JVM primitive without need. Align export/test doubles with composite tenant/entity keys and the shared scoped-port contracts. Preserve explicitly shared catalog semantics.

**Acceptance:** documentation and runnable examples use the same supported abstractions; the same-ID two-tenant contract passes for export/test implementations as well as durable adapters.

## Verification evidence and remaining checks

The following is **test-source coverage**, not a claim that these suites passed during this audit. No Maven tests, application processes, live APIs, or AWS probes were executed. Source files and configuration were inspected directly. Runtime isolation remains unverified where noted.

At the initial audit delivery, all 57 source links resolved and final-newline/trailing-whitespace checks passed. Only the report was added in that increment. The subsequent architecture enforcement delivery is recorded below.

The host `security-audit` skill imposes the execution restriction: “If every control cannot be enforced, do not execute target code.” A compliant runner with denied external networking, an allowlisted empty environment, read-only source/toolchain, scratch-only writes, and resource/time limits was not established for Maven execution. Run the command below only in a runner providing those controls; do not substitute a live production probe.

| Scenario | Existing source evidence | Remaining verification |
| --- | --- | --- |
| Missing or invalid authenticated tenant | `../../apps/documents-api/src/test/java/com/edispark/application/security/TenantContextFilterTest.java` (removed legacy source) tests the legacy filter directly; [DocumentWorkflowAuthorizationTest](../../apps/edispark-api/src/test/java/com/edispark/wireup/security/DocumentWorkflowAuthorizationTest.java) covers the active chain. | Move full claim validation coverage onto the active filter, including blank claims and spoofed headers. |
| Foreign partner/certificate IDs and tenant idempotency | [MftTenantIsolationSecurityTest](../../domains/mft/src/test/java/com/edispark/mft/application/MftTenantIsolationSecurityTest.java) | Run it; extend shared contracts to every domain's reads/writes/deletes and reused IDs. |
| Foreign payload/secret references | [S3EncryptedStorageIsolationTest](../../domains/mft/src/test/java/com/edispark/mft/infrastructure/storage/S3EncryptedStorageIsolationTest.java); [EncryptedSecretStoreTest](../../domains/mft/src/test/java/com/edispark/mft/infrastructure/storage/EncryptedSecretStoreTest.java) | Run local/S3 adapters; add Documents storage isolation contract. Mocked S3 checks do not establish live IAM behavior. |
| Tenant DynamoDB predicates and atomic ownership | [DynamoDbMftRepositoryIsolationTest](../../domains/mft/src/test/java/com/edispark/mft/infrastructure/persistence/DynamoDbMftRepositoryIsolationTest.java) | Run it; add current-context/write-ownership checks above explicit-tenant primitives. |
| Integration list pagination | [IntegrationDynamoStoreTest](../../apps/integration-api/src/test/java/com/edispark/integrationapplication/persistence/IntegrationDynamoStoreTest.java) | Its existing test follows pages for a supplied tenant; it does not prove authentication binding or every domain's isolation. |
| Context cleanup and propagation | [TenantContextTest](../../platform/tenancy/src/test/java/io/cottn/tenancy/TenantContextTest.java) | Nested scopes, exception cleanup, missing-context rejection, alternating tenants on reused worker threads, and handoff checks. |
| Asynchronous MDN ownership | [AsyncMdnIsolationTest](../../domains/mft/src/test/java/com/edispark/mft/application/AsyncMdnIsolationTest.java) | Existing tests address partner/tenant matching and MIC integrity; add per-item context/retry restoration checks. |
| Documents content and pagination | Metadata-first access is visible in `DocumentContentQueryService`; DynamoDB list keeps a tenant predicate. | Two-tenant HTTP/content/storage tests, foreign cursor rejection, schema listing across pages, and same-hash deletion isolation. |
| Global reference data | `MappingService` separates catalog reads from tenant mutation; validator cache contains bundled schemas. | Confirm a tenant cannot mutate shared definitions or publish another tenant's mapping. |
| Deployed trust boundaries | Queue rule/policy and internal-token checks exist in source. | Owner review of actual JWT issuer/audience configuration, internal endpoint exposure, service-token custody, and queue/bus IAM producers. No live validation attempted. |

Focused existing suites for an isolated runner with locally available dependencies:

```bash
./mvnw -o -B -pl apps/edispark-api,apps/documents-api -am test -Dtest=TenantContextTest,TenantContextFilterTest,DocumentWorkflowAuthorizationTest,MftTenantIsolationSecurityTest,S3EncryptedStorageIsolationTest,EncryptedSecretStoreTest,DynamoDbMftRepositoryIsolationTest,AsyncMdnIsolationTest,IntegrationDynamoStoreTest -Dsurefire.failIfNoSpecifiedTests=false
```

This command exercises existing tests only; it does not close the missing scenarios in the table. Missing dependencies must be provisioned outside the isolated test execution rather than allowing the test process external access.

## Delivery sequence and completion criteria

1. **Platform and domain owners: MT 01 with MT 06 contracts.** Start with Exchange as a small scoped-port migration, then Partnerships/SSCC, Transformations, and MFT. Keep business ports domain-owned; put shared context mechanics in platform infrastructure. No HTTP or generated SDK change is needed merely to remove internal tenant plumbing.
2. **Documents owners: MT 02 and MT 05.** Treat the storage migration and schema-index backfill as explicit deployment work. Require dual-tenant content verification before enabling the new backend. Retain current metadata ownership checks throughout.
3. **Platform owners: MT 03 and MT 04.** Standardize HTTP and worker execution adapters while preserving application-specific authorization and trusted ingress contracts. Do not introduce network hops or new services for this work.
4. **All owners: finish MT 06 and MT 07.** Remove migration exceptions, align profile-specific doubles and documentation, and run the complete adversarial contract suite in CI.

Completion means a new ordinary feature can be implemented with business identifiers, tenant-free scoped ports, and no context selection code; missing context and foreign-owned writes fail centrally; every tenant-data backend enforces the same isolation contract; and two-tenant tests cover synchronous, background, retry, content, and pagination paths. Platform operators retain explicitly privileged, auditable tenant-management and work-discovery capabilities.

For rollout, use synthetic two-tenant fixtures in an isolated preproduction environment. Record missing-context failures, ownership rejections, worker event/tenant correlation, and storage migration verification outcomes without logging payloads or secrets. Deployment/IAM assurance remains a separate owner-observed gate, not an inference from source tests.

## Architecture enforcement follow up

The subsequent guardrail increment adds the cross-domain [tenancy architecture test](../../tooling/architecture-tests/src/test/java/com/edispark/architecture/TenancyArchitectureTest.java) and [reviewed migration baseline](../../tooling/architecture-tests/src/test/resources/tenancy-baseline.tsv). It blocks new structural violations and requires resolved exceptions to be removed. The initial baseline records 196 existing rule/member violations, including 77 calls to tenant-selector operations; it does not declare those runtime gaps fixed.

The [OpenSpec remediation tasks](../../openspec/changes/abstract-tenant-isolation/tasks.md) cover MT 01–07, with test-first acceptance criteria, domain ownership, dependencies, and migration gates. [Verification evidence](../../openspec/changes/abstract-tenant-isolation/verification.md) records the architecture test run separately from the runtime/deployment checks that remain planned.
