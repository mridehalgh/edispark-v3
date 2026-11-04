# Local demo data coverage

This is the canonical coverage manifest for the deterministic `EdiSpark Local` workspace. Every user-facing feature change must assess and, when necessary, update this document, its fixture reconciler, and its behavior tests.

The goal is not a large synthetic database. The goal is a small, safe set of connected examples that lets a developer exercise every supported journey immediately after `pnpm dev`.

## Workspace identity

| Concern | Stable identity | Owner |
|---|---|---|
| Better Auth administrator | `local@edispark.test`, owner | `apps/web/src/lib/auth.ts` |
| Better Auth operations user | `operations@edispark.test`, member | `apps/web/src/lib/auth.ts` |
| Organization | `local-test-workspace` / `EdiSpark Local` | `apps/web/src/lib/auth.ts` |
| Internal tenant | `LOCAL_SEED_TENANT_ID` | `tooling/scripts/local-environment.mjs` |
| Organization-to-tenant mapping | One-to-one row in `organization_tenant` | `apps/web/src/lib/auth.ts` |
| Local hierarchical root | `alias/edispark/local/hierarchical-root` | `LocalKms` |
| Local tenant branch | `tenant/{tenantId}` | `LocalKms` |

All local services must resolve the same internal tenant. A new tenant-aware service must join this mapping rather than inventing another demo tenant.

## Feature coverage manifest

| Feature journey | Stable fixture keys and expected states | Reconciler | Behavior evidence |
|---|---|---|---|
| Authentication and workspace activation | Verified local user, owner membership, active `EdiSpark Local` organization, stable tenant mapping | `prepareLocalAuth` | Full-stack startup and local sign-in flow |
| Role-aware product workspace | Stable owner and member personas share the same tenant; members see Documents while owners can open Overview, Files, Partners, Connections, and Organisation | `prepareLocalAuth`, local test-login route | Local tools persona switch, web typecheck, route and API authorization tests |
| Unified admin workspace | Existing partner, AS2 connection, certificates, transfer states, business documents, and provenance populate the human-facing Overview, Files, Connections, Partners, and Organisation journeys without a second fixture model | Existing Documents, MFT, Integration, and auth reconcilers | Full-stack startup, canonical route smoke checks, web typecheck, and production build |
| Documents inbound processing | Retailer order `PO-2026-0042`: eight meaningful TRADACOMS 9 lines, successful parse, and a complete UBL 2.1 JSON derivative | `DocumentSetSeeder` (`supplier-order-invoice-v3`) | `DocumentSetSeederTest` creation, direction, derivative, stale-fixture migration, and repair coverage |
| Documents business documents | Outbound supplier invoice `INV-100045` answers `PO-2026-0042` with the same eight lines and reconciled allowances, tax, and totals; `CN-1007` is an outbound follow-up | `DocumentSetSeeder` (`supplier-order-invoice-v3`) | `UblSeedDocumentTest` model parsing and field-depth assertions plus seeder repair/idempotence coverage |
| MFT partner configuration | EDIFACT retailer `Northstar Retail` (`SIMULATOR`) and TRADACOMS retailer `Harbour Retail` (`SIMULATOR_TRADACOMS`), both using local AS2 ID `EDISPARK` | `LocalMftDemoSeeder` | `LocalMftDemoSeederTest` partner-specific flow, repair, and idempotence coverage |
| MFT certificate operations | One disposable local identity and one partner public X.509 certificate, managed alongside secure connections at `/connections/certificates` | `LocalMftDemoSeeder` | MFT startup, authenticated connection-certificate route, and certificate parsing/secret-store tests |
| MFT retailer onboarding pack | Tenant-scoped `EDISPARK` connection details, local receiving and asynchronous MDN URLs, supported algorithms, fingerprint, and downloadable public certificate | `LocalMftDemoSeeder`, MFT connection-details API | `ConnectionDetailsServiceTest`, generated API contract, and web build |
| MFT inbound operations | `PO_45001235.edi` from Northstar and `PO_HARBOUR_2026_1187.edi` from Harbour are delivered eight-line retailer orders with processed MDN evidence | `LocalMftDemoSeeder` | `LocalMftDemoSeederTest` partner-specific direction, full-envelope assertions, payload repair, partial repair, and idempotence coverage |
| MFT outbound operations | Matching eight-line EDIFACT and TRADACOMS supplier invoices are delivered; additional Northstar DESADV, INVRPT, and REMADV files retain awaiting-MDN, retry, and needs-review operational states | `LocalMftDemoSeeder` | `LocalMftDemoSeederTest` partner-specific direction, state coverage, payload repair, and repeat-run idempotence |
| MFT live local exchange | Two eight-line orders in `local-inbound-orders-batch.edi` and one eight-line `local-outbound-invoice.edi` share the seeded commercial values and are exchanged on demand from the local tools dock | `pnpm dev`, local simulator BFF | Authenticated local UI trigger, multi-message fixture regression test, real AS2 exchange, MFT transfer evidence, and simulator peer activity |
| Trading-partner control plane | `Northstar Retail` has strict inbound-file failure handling with EDIFACT Order and Invoice routes; `Harbour Retail` demonstrates keep-valid-message handling with TRADACOMS routes; Northstar also has editable and scheduled revisions | `LocalIntegrationDemoSeeder` using both reconciled MFT partner IDs | Seeder policy/relationship assertions plus partnership lifecycle, mapping, exchange, envelope writer, and web typecheck tests |
| Versioned configuration lifecycle | Stable unused mapping and partner drafts, a tenant mapping and agreement scheduled for `2099-01-01T09:00:00Z`, and retained global revisions that demonstrate fork, revise, schedule, archive, guarded deletion, and safe draft deletion | `LocalIntegrationDemoSeeder`, `ClasspathGlobalMappingCatalog`, `LocalMftDemoSeeder` | Seeder repair/idempotence tests plus domain lifecycle tests |
| Visual mapping publication | Eight immutable EdiSpark-vended global mappings for inbound/outbound UBL Order/Invoice × EDIFACT D96A/TRADACOMS 9. Every published source and target pointer is populated, and every sample contains eight commercial lines. One editable tenant draft and one scheduled tenant revision remain available | `ClasspathGlobalMappingCatalog`, `LocalIntegrationDemoSeeder` | `ClasspathGlobalMappingCatalogTest` field-completeness and line-depth checks, `MappingServiceTest`, mapping-preview tests, scheduling, deletion guard, and immutability behavior |
| Inbound MFT-to-Documents route | Delivered files are hash-verified, split into independently routed business messages, executed through each selected mapping, and retained as one DocumentSet per message with transfer/ordinal correlation; partner revisions choose strict or keep-valid failure handling | `LocalInboundMftReconciler`, `LocalMftDemoSeeder`, `LocalIntegrationDemoSeeder` | Batch splitting, review healing, per-message idempotency, deterministic Documents ingestion, authenticated local **Receive order batch** simulation, and derivative inspection through the Documents API |
| Document-to-AS2 send | Order and invoice detail views resolve active routes, generate EDI, retain mapping-specific derivatives, and submit AS2 with full provenance | Unified API in-process module adapters plus Documents and MFT demo services | `ExchangeServiceTest`, `EdiPayloadWriterTest`, unified context test, generated contracts, and local end-to-end route |
| Local cryptographic isolation | Shared test root, tenant branch, resource-derived key; ciphertext bound to tenant and resource | `LocalKms` | `LocalKmsBehaviorTest`, payload integrity, and encrypted secret-store tests |
| MFT organisation-admin guidance | `/mft/docs` partner setup, certificates, receipt statuses, troubleshooting, and compatibility guidance | MFT setup guide | Web typecheck/build and local route verification |

## Adding or changing a feature

A feature change is complete only when one of these is true:

1. This table identifies its representative journey, stable fixture keys, reconciler, and behavior evidence; or
2. the change has no observable demo-data impact, and the handoff explicitly says why.

For data-bearing changes:

- create realistic, comprehensive user-facing examples for success and operationally important non-success states; focused unit-test inputs may remain minimal when they are not exposed as product samples;
- use generic placeholders instead of real PII;
- reconcile by stable business identity, not by row count;
- repair missing fixtures and migrate stale fixture shapes without deleting unrelated local data;
- prove creation, repair, and repeat-run idempotence in behavior tests;
- verify the fixture through the real API or UI, not only by inspecting storage.
- add local-only interactive simulations to the shared local tools dock rather than creating independent floating controls.

Update this manifest in the same change whenever a journey, status, entity, or fixture key changes.
