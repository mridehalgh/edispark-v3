# Plan

- [x] Publish and read tenant-scoped immutable lookup revisions with exact-key matching.
- [x] Support a lookup operation and bind each table alias to an agreement capability.
- [x] Verify tenant isolation, unknown keys, previous revision replay and local test fixtures.
- [x] Add CSV preview, publishing and route binding to the partner experience through the generated API client.
- [ ] Define workspace business profile and relationship inputs, then surface them in managed setup progress.
- [ ] Check test-to-live promotion and retailer acceptance against relevant business and transport configuration.

## Verification

- Mapping runtime tests exercise repeated fields, exact keys, missing values and replay with a prior revision.
- Storage tests exercise conditional publication, immutable values and independent ownership for the same revision ID.
- Authenticated local API smoke test verified the seeded table, publishing a second revision, mapping test and publish, and agreement qualification before and after binding.
- OpenAPI export, SDK generation, contract validation, web typecheck and CSV tests passed.
