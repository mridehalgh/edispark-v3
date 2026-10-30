# Local demo data contract

Every user-facing feature must remain demonstrable in the deterministic local workspace. A feature is not complete until its local-demo impact has been assessed.

## Required feature gate

For every new or changed page, workflow, entity, status, permission, integration, or operational state:

1. Use the repository `local-demo-workspace` skill.
2. Identify the representative local journey and the minimum safe data needed to exercise it.
3. Update `docs/local-demo-data.md` when coverage, ownership, stable fixture keys, or expected scenarios change.
4. Add or change a local-profile fixture reconciler. Use stable business keys so startup can repair missing or stale fixtures without deleting unrelated developer data.
5. Add behavior tests proving the expected fixture is created, a missing fixture is repaired, and a second run does not duplicate it.
6. Run the affected service tests and start the relevant local flow. Verify the data is visible through the same API/UI path a developer uses.

Pure refactors, infrastructure changes with no observable local state, and production-only adapters may record “no demo-data impact” in the handoff instead of inventing a fixture. This exception must be deliberate; existing demo behavior must still pass.

## Design constraints

- Keep sample data free of real PII, credentials, customer identifiers, and production certificate material.
- Seed through application/domain entry points where practical so fixtures obey real validation and tenant isolation.
- Keep fixture logic in local-profile infrastructure, never in production domain behavior.
- Reconcile named fixtures; do not use “the table is non-empty” as evidence that the demo is complete.
- Preserve developer-created local data. Do not reset the workspace during normal startup.
- If a fixture shape changes incompatibly, implement an explicit local migration or versioned replacement. Reserve `pnpm local:reset` for unrecoverable generated state and disclose the data loss.
- Keep the local organization, JWT tenant, Documents tenant, MFT tenant, and local KMS alias anchored to `LOCAL_SEED_TENANT_ID`.
