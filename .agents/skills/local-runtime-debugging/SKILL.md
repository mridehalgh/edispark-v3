---
name: local-runtime-debugging
description: Diagnose and fix errors in the running EdiSpark local stack by reproducing them through authenticated APIs, correlating runtime evidence with source and persisted state, and verifying the correction end to end. Use for pasted stack traces, repeated scheduler failures, unexpected 4xx/5xx responses, stuck transfers or exchanges, and UI errors backed by localhost services.
---

# Debug the EdiSpark Local Runtime

Turn the reported symptom into a reproducible request, identify the smallest responsible boundary, fix the underlying behavior, and prove the same local journey works afterward.

## Establish the evidence

1. Preserve the user's error text, timestamp, endpoint, visible status, IDs, and correlation ID. Do not ask for information already present in the error.
2. Check `git status --short` and preserve unrelated work.
3. Check the running services before changing them:
   - web and auth: `http://localhost:3000/api/auth/ok`
   - unified API: `http://localhost:8080/actuator/health`
   - AS2 simulator when relevant: `http://localhost:8082/actuator/health`
4. Distinguish the first application exception from stream, scheduler, servlet, or reflection frames. Repeated scheduled output is usually one poison record being retried, not many independent failures.
5. Trace identifiers through the API response and domain records. Prefer tenant-scoped APIs over direct database inspection.

Read [references/local-api-map.md](references/local-api-map.md) when selecting an API or authenticating. Read [references/failure-routing.md](references/failure-routing.md) when mapping a stack frame or status to its owning module and verification command.

## Reproduce through the real API

Use the bundled probe for ordinary JSON APIs. It signs in through the development-only local BFF, holds cookies only in memory, supplies a fresh correlation ID, redacts sensitive fields, and never prints bearer tokens:

```text
node .agents/skills/local-runtime-debugging/scripts/local-api.mjs health
node .agents/skills/local-runtime-debugging/scripts/local-api.mjs request GET /api/integration/exchanges --persona member
node .agents/skills/local-runtime-debugging/scripts/local-api.mjs request GET /api/mft/transfers
```

For an authorized state-changing reproduction, put the JSON request under `/tmp` and pass `--body-file`. Do not add credentials, real PII, customer payloads, or production data.

```text
node .agents/skills/local-runtime-debugging/scripts/local-api.mjs request POST /api/integration/exchanges --persona member --body-file /tmp/exchange-request.json
```

- Prefer the same BFF route the UI uses. This exercises authentication, generated clients, error propagation, and the backend together.
- Reuse the returned correlation ID when searching captured logs or comparing an error response.
- Do not request binary `/content` endpoints with the probe. Inspect payloads only when essential, write them to a temporary file, and never print EDI, certificates, keys, cookies, or tokens into the conversation.
- A failing HTTP response is evidence. Record its status, safe error code, stage, retryability, recommended action, and bounded details before editing.

## Find and fix the cause

Follow the request from BFF route to generated client, controller, application service, domain behavior, repository mapper, and worker or scheduler. Stop once the responsible contract is proven.

- Treat persisted legacy values as a compatibility problem when current writes are canonical but old local rows cannot hydrate. Add a bounded read migration or reconciler; do not reset the workspace to hide it.
- Treat missing mapping input as a source/mapping contract problem. Verify the actual fixture and parsed document before weakening validation.
- Treat poison work as an isolation problem. One bad transfer, message, or exchange must receive a durable reason/status or bounded retry outcome without aborting unrelated work.
- Persist safe operator-facing reasons for `NEEDS_REVIEW` and `FAILED`; logs alone are insufficient.
- Do not expose exception internals, payload data, secrets, filesystem locations, or credentials in API/UI errors.
- Add a regression test at the lowest observable boundary and an integration or fixture test when module composition caused the failure.

If the user asked only for diagnosis, stop after evidence-backed root cause. A request to fix the local error authorizes scoped code and test changes, not data deletion or unrelated cleanup.

## Restart deliberately

Do not restart a healthy process just to investigate it. Java changes require a rebuilt API process; Next.js usually hot reloads UI changes.

`pnpm dev`, `pnpm dev:api`, and `pnpm dev:web` terminate existing listeners on their managed ports before starting. State which processes will be replaced and obtain approval when they may belong to the user or contain unsaved work. Never use `pnpm local:reset` unless reconciliation cannot repair generated state and the user explicitly accepts deletion of `.local` data.

## Verify the exact journey

1. Run the narrow behavior test, then the owning module tests from [references/failure-routing.md](references/failure-routing.md).
2. Rebuild or restart only the affected runtime when necessary.
3. Replay the same API request with the probe and confirm the expected response and persisted state.
4. Verify a neighboring record still progresses when the bug involved scheduled or queued work.
5. For a UI symptom, verify the same authenticated browser/BFF path, including the operator-facing reason and recovery behavior.
6. Run `git diff --check` and report any boundary that could not be exercised.

Finish with the root cause, changed behavior, exact verification, whether a restart is still required, and any intentionally preserved dirty-tree work.
