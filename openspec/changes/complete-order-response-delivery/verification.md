# Verification and scope

Evidence retained locally on 2026-11-09–13. No production connection was used.

## Actual corrected example

- Document: `6d10dd3f-3060-4079-9605-11eddb6639c8/24568469-b3da-488f-9e3e-137cda5552f3`.
- Exchange: `74a0b715-141f-44c6-866d-441cb0539115`; transfer: `f5f9ade1-7982-4954-824e-5ed5c56e0b24`.
- Delivered with processed MDN; all eight line IDs, quantities, units, names and supplier references checked.
- Payload SHA-256: `7483955836d453c7c004d876f04c3183e0089fa081d97949f298471b0cf650af`.
- Replayed after actual stack restarts with the same exchange, transfer and hash. Original final source hash unchanged.
- AP header and all eight Accepted decisions are in a new linked correction. Historical final bytes were not rewritten.

## B&Q-only profile

- Document: `d1918c6c-727f-4b14-91f3-3a3ad41bb9dc/821df4cb-785e-45f3-9464-a435c9de1fc7`.
- Exchange: `b8bbd8ba-c593-4a58-8d0d-715e7fda459d`; transfer: `bca54118-012a-4930-87e8-d3d5cf49d84a`.
- Delivered with processed MDN. Actual wire bytes checked for all eight EANs, quantities, units and original line references.
- Payload SHA-256: `f0383172fd438526e917aa4eece7948d08d1c75b0e634487fcbff9830e3a05d0`.
- Three repeated readiness checks created no exchange. Missing-vendor readiness returned a DOCUMENT finding at the editable source path; attempted send retained validation evidence without EDI or transport.
- Idempotent replay returned the same exchange. Sequential controls advanced across earlier stack restarts and were not reset.
- Simulator defects in TRADACOMS receiver/private-key alias selection and reversed MDN signing identity were fixed and regression tested. Failed attempts remain recorded; the delivered attempt uses transmission/file generation 3.
- The additional restart planned after this successful delivery was interrupted before starting. A post-delivery B&Q restart/replay check is not claimed.

## Automated checks

- Standards: independent model and wire validation, escaping, malformed controls, decision consistency, identifiers, dates, numbers and 1/8/12/10,000 lines.
- Mapping and qualification: complete repeats, explicit omission reports, unexpected-field rejection, source/target validation, stale rule evidence, JSON structural fixture equality and golden B&Q bytes.
- Persistence: mapping rehydration, DynamoDB first-page pagination and tenant/connection/exchange-bound sequential control replay across provider instances.
- Fixtures: create, repair, repeat-run idempotence, linked purchase order and preserving suspended agreements.
- Authorization/recovery: exact retained source, stale source 409, member restriction, tenant boundaries and typed API advice.
- Receipt handling: processed/negative/pending results, functional acknowledgement distinction, neighbouring transfer isolation and failed worker isolation.
- Timeout/retry: an injected retryable socket timeout schedules the existing transfer, allows its neighbour to complete, and delivers the same payload/transfer on the second attempt without a third dispatch. Passing log: `/tmp/bq-timeout-retry-test.log`.
- Logs: `/tmp/bq-full-slice-tests.log` (standards/mapping/exchange checks; later fixture failure corrected), `/tmp/bq-recovery-tests.log` (fixture/persistence checks; JSON ordering failure corrected), `/tmp/bq-proof-test-2.log` (passing qualification), `/tmp/bq-delivery-final-regressions.log` (passing golden byte/transport checks), `/tmp/bq-simulator-final-tests.log`.
- Contract export and SDK generation passed. Web: 88 tests, TypeScript and production build passed.
- Authenticated Chrome desktop/mobile checks passed for numbered activity, processed MDN, retained-source testing and draft recovery, stale source conflict, member denial and no page-level horizontal overflow. Mobile timeline screenshot inspected.

## Limits and subsequent work

- The B&Q full-acceptance subset follows the published ACK v1.4 complete-file example, including ACKTLR:4. The guide's trailer-table spelling discrepancy still needs production partner qualification.
- Screwfix is excluded. The Ocado guide does not define ACK; no Ocado acknowledgement profile is claimed.
- Other five document types remain in the broader six-document proposal; this change does not claim production completeness for them.
- Earlier in-memory exchange history was lost before the durable metadata correction. Retained document bytes remain intact; unavailable historical exchange records were not fabricated.
