# Architecture checks

This module participates in the existing backend CI reactor. It imports all five domain modules and fails if a domain or its application classes are missing.

## Tenant boundary checks

`TenancyArchitectureTest` checks business code in domain and application packages:

| Rule | Rejected responsibility |
| --- | --- |
| `CONTEXT_ACCESS` | Reading or binding execution context through `TenantContext`, its scoped types, or token/context holders |
| `RAW_STORAGE` | Depending on an AWS SDK client instead of a domain port |
| `INFRASTRUCTURE_DEPENDENCY` | Depending on infrastructure classes or interfaces from business code |
| `TENANT_SELECTOR` | A repository/store interface accepting a typed tenant ID or a classified string tenant selector |
| `TENANT_SELECTOR_CALL` | Business code calling or referencing an existing tenant-selector operation, including typed selectors |
| `UNCLASSIFIED_STRING_PORT` | A new string input on a repository/store interface without an exact reviewed classification |
| `PRIVILEGED_PORT` | A classified global discovery operation exposed on an ordinary business port |
| `GLOBAL_DISCOVERY_DEPENDENCY` | Business code depending on a port that also exposes global discovery |

Infrastructure ingress and worker adapters may establish trusted context. Tenant ownership fields, event metadata, scoped business ports, and deliberately shared reference catalogs remain legal. These are structural checks: they cannot infer whether an arbitrary business string or operation is secretly used as a tenant selector, or prove that persistence correctly enforces isolation. Runtime two-tenant contract tests remain necessary.

## Reviewed migration debt

`src/test/resources/tenancy-baseline.tsv` contains exact rule/member keys, audit finding IDs, and reasons. The baseline is now empty after runtime remediation. The historical initial scan recorded 196 violations; every exception has been removed. They map to MT 01 in the [audit](../../docs/architecture/multi-tenancy-abstraction-audit.md).

Tests never write the baseline. A new violation fails CI. A resolved entry also fails CI until removed, so obsolete exceptions cannot silently authorize future regressions. Source line numbers are omitted; method signatures and dependency descriptions remain part of each key. Do not accept failures by regenerating the baseline or adding package-wide exceptions.

`tenancy-port-classifications.tsv` records exact bytecode signatures and a review reason for business strings and infrastructure-only privileged discovery. New string inputs fail until classified as `tenant`, `global`, or an explicitly justified `business` input. Tenant classification is not an exemption: it produces a violation that must be fixed or already exist in the migration baseline. Removed signatures fail the classification existence check and must be deleted too. Array parameters use JVM names such as `[B` for `byte[]`.

The [OpenSpec tasks](../../openspec/changes/abstract-tenant-isolation/tasks.md) describe how to eliminate each family of exceptions. Removing an existing violation must not be accompanied by adding an equivalent new violation elsewhere.

## Verification

The backend CI job already runs `verify` for this module. A focused invocation is:

```bash
./mvnw -B -pl tooling/architecture-tests -am test -Dtest=TenancyArchitectureTest,TenancyGuardrailTest,PaginationArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.forkCount=1
```

`TenancyGuardrailTest` imports small fixtures without executing business logic. It verifies rejected context/storage/privileged dependencies, typed and string selectors, legal ingress and ownership data, and baseline growth, cleanup, duplicate, and provenance failures.

See the [current increment's verification evidence](../../openspec/changes/abstract-tenant-isolation/verification.md). These checks enforce code boundaries and do not constitute production tenant-isolation certification.
