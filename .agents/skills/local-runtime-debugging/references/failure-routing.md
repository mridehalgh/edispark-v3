# Failure routing

Use the first EdiSpark application frame or failing API stage to select the owner. Read the repository rules routed by `AGENTS.md` before changing that area.

| Evidence | Likely owner | Narrow verification |
|---|---|---|
| `documents` controller, mapper, format, derivative, version | `domains/documents` | `./mvnw -B -pl domains/documents -am test` |
| Mapping graph/runtime/catalog error | `domains/transformations` | `./mvnw -B -pl domains/transformations -am test` |
| Agreement, partner, capability, SSCC | `domains/partnerships` | `./mvnw -B -pl domains/partnerships -am test` |
| Exchange lifecycle or outbound mapping orchestration | `domains/exchange` | `./mvnw -B -pl domains/exchange -am test` |
| AS2 transfer, receipt, retry, certificate, payload storage | `domains/mft` | `./mvnw -B -pl domains/mft -am test` |
| Inbound split/translation, local reconciler, EDI writer | `apps/integration-api` | `./mvnw -B -pl apps/integration-api -am test` |
| Spring composition or real local fixture contract | `apps/edispark-api` | `./mvnw -B -pl apps/edispark-api -am test` |
| Next.js BFF, generated-client propagation, operator UI | `apps/web` | `pnpm --filter @edispark/web test` then `pnpm --filter @edispark/web exec tsc --noEmit` |

## Common patterns

- `IllegalArgumentException` during mapping: inspect the mapping node, actual parsed input, and static fixture. Do not default a required business value without a product rule.
- `No enum constant ...`: find the persistence or request-boundary `valueOf`; determine whether the value is invalid input, a renamed enum, or legacy data needing compatible hydration.
- Repeated scheduled trace at a fixed interval: identify the stable event/transfer key and confirm success, quarantine, retry, or DLQ state is recorded per item.
- `NEEDS_REVIEW` without explanation: trace the transition detail/failure reason through persistence, DTO/client, BFF, and UI.
- UI error differs from backend error: compare the BFF response and generated client error mapping before changing the domain.
- Fix works only after reset: reject it unless reset is the explicit product behavior. Add reconciliation or backward compatibility for deterministic local state.

Run focused tests first. Use the complete owning-module suite when persistence, scheduling, security, or module wiring changed. Use the full `apps/edispark-api` reactor for cross-module composition changes.
