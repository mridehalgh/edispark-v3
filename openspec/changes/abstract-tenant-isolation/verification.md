# Tenant isolation remediation verification

Date: 2026-11-21. Base source: `61e41f158ddad7b6e195337080d689093c7cbe50`, plus this change.

## Delivered behavior

All five domains expose scoped business ports and ownership creation capabilities. Infrastructure resolves tenant authority; foreign writes and absent-context operations fail. MFT privileged discovery/AS2 ingress is separate. Both delivery workers bind before leases and restore after each item. Executor propagation captures both a tenant and absence of authority, including global scheduled discovery without accidental inheritance. The unified JWT filter parses claims separately from downstream business execution; unused legacy filters are removed.

Documents stores tenant-namespaced authenticated ciphertext locally and in S3, verifies hashes, and fails invalid production configuration. Shared cryptographic primitives preserve MFT's existing service/encryption context. Metadata repositories require explicit infrastructure scope, follow all pages, query the tenant schema GSI, and reject malformed/foreign continuation keys before I/O. Foundation/local provisioning includes the index; administrative migration/backfill tools provide dry-run, copy and verification receipts while retaining originals. Local startup upgrades retained data before fixture reconciliation.

The ArchUnit migration baseline is empty (initial scan: 196 violations). There are 17 exact port classifications, including business strings and privileged infrastructure signatures. Rules reject context helpers, AWS clients, infrastructure classes/interfaces, selector methods/call sites and mixed global discovery in ordinary business code. Structural checks cannot establish storage correctness or live authorization.

## Executed checks

- Full affected backend reactor: **1,086 tests across 246 report suites, zero failures/errors/skips**, including 13 architecture tests. This includes existing regression suites, scoped durable/disposable contracts, HTTP authorization, worker recovery, seeder repair/idempotence, encrypted-content integrity and synthetic migration tests. jqwik properties use at least 100 trials for context cleanup, repository tenant isolation and paginated schema completeness.
- Isolated Maven `-Popenapi-export verify -DskipTests`: passed; all three exported OpenAPI documents are semantically identical to committed contracts. `pnpm contracts:check` passed, including generation, validation and SDK type checks.
- Foundation Terraform formatting/validation and mock-provider plan test passed (one test). Existing provider deprecation warnings remain. No Terraform apply or live AWS access was performed.
- Strict OpenSpec validation passed. Final whitespace/link checks are recorded with release preparation.

Backend tests used a disposable source copy, offline cached Maven 3.9.9 and native ARM Corretto 24.0.1, compiling release 21. CI uses Java 21; a remote CI run is separate. The macOS execution sandbox denied external networking and host-user file contents outside toolchain/cache allowlists, allowed loopback for embedded DynamoDB and local HTTP tests, and confined writes to scratch/device paths. JVM heap/class-space, CPU, file-size and wall-time limits bounded execution; Mockito's cached agent was preloaded because JVM self-attachment is restricted. The OpenAPI build used a larger file-size limit for the executable jar. OS process memory limits were unavailable, so these are bounded engineering checks, not a full security-audit execution claim.

Equivalent commands:

```sh
./mvnw -B -pl apps/edispark-api,tooling/architecture-tests -am test
./mvnw -B -pl apps/edispark-api -am -Popenapi-export verify -DskipTests
pnpm contracts:check
terraform -chdir=infrastructure/terraform/foundation test
OPENSPEC_TELEMETRY=0 openspec validate abstract-tenant-isolation --strict
```

Complexity review: the administrative legacy-blob reconciliation and schema-index backfill each retain several independent validation/receipt branches (approximately seven and six paths respectively). They are bounded migration operations with direct behavioral coverage. Existing domain orchestration complexity was not expanded into a general cleanup project.

## Release gates

[Migration instructions](../../../docs/architecture/documents-storage-migration.md) define the required complete tenant inventory, index/backfill visibility check, frozen final copy reconciliation, preproduction two-tenant workflow acceptance and rollback rehearsal. These have not been executed against deployed environments. Actual IAM producer grants, endpoint/internal-token custody, JWT issuer settings and key readiness require deployment-owner evidence. Do not infer these from mocks or apply this runtime over retained production data before the gates pass. The tool does not delete legacy data or supply a plaintext runtime fallback.

## Local retained-workspace verification

`pnpm dev` started the unified API, embedded persisted DynamoDB, AS2 simulator and web app on the existing local workspace. Startup verified 66 retained content references with zero unavailable references and retained legacy files. Documents, MFT and Integration seed reconciliation completed. Local demo persona sign-in returned 200; authenticated list calls returned Documents pages, 20 schemas, three MFT partners, two certificates, 20 transfers, four trading partners, one SSCC issuer and 30 combined tenant/shared mappings. Three retained current document versions downloaded successfully through authenticated metadata-authorized content endpoints. Authenticated Documents, MFT and MFT documentation pages returned 200. The initial demo retry attempted delivery before the simulator started, then resumed normally.

The in-app browser JavaScript tool was unavailable after tool discovery; browser interaction is unverified. Direct authenticated API checks cover the runtime/data boundary but do not assert rendered interactive behavior.
