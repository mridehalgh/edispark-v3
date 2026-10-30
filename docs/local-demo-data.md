# Local demo data coverage

This is the canonical coverage manifest for the deterministic `EdiSpark Local` workspace. Every user-facing feature change must assess and, when necessary, update this document, its fixture reconciler, and its behavior tests.

The goal is not a large synthetic database. The goal is a small, safe set of connected examples that lets a developer exercise every supported journey immediately after `pnpm dev`.

## Workspace identity

| Concern | Stable identity | Owner |
|---|---|---|
| Better Auth user | `local@edispark.test` | `apps/web/src/lib/auth.ts` |
| Organization | `local-test-workspace` / `EdiSpark Local` | `apps/web/src/lib/auth.ts` |
| Internal tenant | `LOCAL_SEED_TENANT_ID` | `tooling/scripts/local-environment.mjs` |
| Organization-to-tenant mapping | One-to-one row in `organization_tenant` | `apps/web/src/lib/auth.ts` |
| Local data-key alias | `alias/edispark/tenant/{tenantId}/data` | `LocalKms` |

All local services must resolve the same internal tenant. A new tenant-aware service must join this mapping rather than inventing another demo tenant.

## Feature coverage manifest

| Feature journey | Stable fixture keys and expected states | Reconciler | Behavior evidence |
|---|---|---|---|
| Authentication and workspace activation | Verified local user, owner membership, active `EdiSpark Local` organization, stable tenant mapping | `prepareLocalAuth` | Full-stack startup and local sign-in flow |
| Documents inbound processing | `PO-2026-0042`, TRADACOMS source with successful parse and UBL JSON derivative | `DocumentSetSeeder` | `DocumentSetSeederTest` creation, derivative, and repair coverage |
| Documents business documents | `INV-100045` and `CN-1007`, UBL 2.1 JSON with matching schemas | `DocumentSetSeeder` | `DocumentSetSeederTest` creation, schema, repair, and idempotence coverage |
| MFT partner configuration | `Northstar Trading`, local AS2 ID `EDISPARK`, remote AS2 ID `SIMULATOR` | `LocalMftDemoSeeder` | MFT startup smoke verification |
| MFT certificate operations | One disposable local identity and one partner public X.509 certificate | `LocalMftDemoSeeder` | MFT startup plus certificate parsing/secret-store tests |
| MFT inbound operations | `PO_45001235.edi`, delivered with processed MDN evidence | `LocalMftDemoSeeder` | `LocalMftDemoSeederTest` partial repair and idempotence coverage |
| MFT outbound operations | Delivered invoice, awaiting-MDN ASN, scheduled-retry inventory, and needs-review remittance | `LocalMftDemoSeeder` | `LocalMftDemoSeederTest` state coverage and repeat-run idempotence |
| Local cryptographic isolation | Tenant-derived data key; payload and certificate ciphertext bound to tenant and resource | `LocalKms` | `LocalKmsBehaviorTest`, payload integrity, and encrypted secret-store tests |
| MFT operator guidance | `/mft/docs` production KMS, certificate, tenant-isolation, and compatibility guidance | MFT docs page | Web typecheck/build and local route verification |

## Adding or changing a feature

A feature change is complete only when one of these is true:

1. This table identifies its representative journey, stable fixture keys, reconciler, and behavior evidence; or
2. the change has no observable demo-data impact, and the handoff explicitly says why.

For data-bearing changes:

- create realistic minimal examples for success and operationally important non-success states;
- use generic placeholders instead of real PII;
- reconcile by stable business identity, not by row count;
- repair missing fixtures and migrate stale fixture shapes without deleting unrelated local data;
- prove creation, repair, and repeat-run idempotence in behavior tests;
- verify the fixture through the real API or UI, not only by inspecting storage.

Update this manifest in the same change whenever a journey, status, entity, or fixture key changes.
