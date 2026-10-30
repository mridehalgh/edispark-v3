---
name: local-demo-workspace
description: Provision, repair, verify, or extend the EdiSpark local demo workspace so its deterministic user, organization-to-tenant mapping, Documents fixtures, MFT partners/certificates/transfers, and local KMS test double remain complete. Use when changing local startup, seed data, demo authentication, local tenant identity, MFT sample data, or local encryption behavior.
---

# Local Demo Workspace

Keep local development immediately usable after `pnpm dev`. Treat the demo workspace as a small, versioned product fixture rather than unrelated sample rows.

## Preserve the identity contract

- Use the local credentials supplied by `tooling/scripts/local-environment.mjs`; never hardcode production credentials or real PII.
- Map the Better Auth organization `local-test-workspace` to exactly one internal tenant ID through `organization_tenant`.
- Use `LOCAL_SEED_TENANT_ID` as the stable tenant ID for auth, Documents, MFT, and future tenant-aware local services.
- Preserve the database constraints: one organization has one tenant and one tenant belongs to one organization.
- Do not equate an organization with every KMS resource. The tenant owns one logical symmetric data-encryption key. AS2 signing and decryption identities follow the separate cell-key design documented under `docs/mft/`.

## Keep the fixture manifest complete

Ensure startup idempotently reconciles, rather than blindly duplicates or skips, these minimum fixtures:

- one verified local demo user and one local workspace organization;
- the organization-to-tenant mapping;
- Documents examples for Order `PO-2026-0042`, Invoice `INV-100045`, and Credit Note `CN-1007`;
- the parsed UBL derivative for the TRADACOMS order;
- one MFT partner, one local identity certificate, and one remote public certificate;
- MFT transfers representing delivered inbound, delivered outbound, awaiting MDN, retry scheduled, and needs-review states.

When adding a fixture, give it a stable business key and update this manifest plus the local-development documentation. Seeders must repair a missing fixture while leaving user-created local records alone.

## Preserve local KMS parity

- Keep application code behind the same cryptographic storage ports used by production-oriented code.
- The local KMS test double may keep one disposable root secret below `.local`, but derive a distinct logical key for each tenant alias: `alias/edispark/tenant/{tenantId}/data`.
- Authenticate ciphertext with `tenant_id`, `service`, `resource_type`, and `resource_id`. A different tenant or resource must fail decryption.
- Keep all local key files, PKCS#12 identities, passwords, and encrypted payloads under ignored `.local` paths with owner-only permissions.
- Never present local derivation as a production security boundary. Production uses AWS KMS and stores only key references and public certificate material.
- Keep the disposable PKCS#12 bridge local-only while `phase2` requires it. Never add a production fallback that exports or stores a KMS private key.

## Implement safely

1. Read `AGENTS.md` and only the routed rules relevant to the files being changed. For tenant-aware MFT changes, include the multi-tenancy, SaaS planes, reliability, testing, and Java/Spring rules.
2. Trace the complete flow from local environment variables through auth mapping, tenant context, seeder, repository, and UI before editing.
3. Put fixture reconciliation in local-profile infrastructure components. Do not branch on “local” inside domain behavior.
4. Use generic placeholders in EDI and UI fixtures. Do not add real people, addresses, phone numbers, or payment details.
5. Prefer repairing specific missing stable fixtures. Only propose `pnpm local:reset` when state cannot be reconciled; call out that it deletes generated local state before running it.
6. Update `README.md` and the relevant file under `docs/` whenever the identity, cryptographic, sensitive-data, or fixture contract changes.

## Verify the workspace

Run the narrowest relevant checks, then verify startup behavior:

```text
./mvnw -B -pl domains/documents -am test
./mvnw -B -pl apps/mft-api -am test -DskipITs
pnpm exec tsc --noEmit
pnpm dev
```

Confirm:

- startup logs report a complete Documents catalogue and MFT demo;
- local sign-in opens the same tenant in Documents and `/mft`;
- `/mft` displays partner, certificate, and transfer examples without manual setup;
- `/mft/docs` and MFT health are available;
- cross-tenant and wrong-resource local decryption fail;
- a second seeding pass creates no duplicate business fixtures;
- `git diff --check` passes.

If a service cannot be run, report the exact unverified boundary rather than claiming the demo is complete.
