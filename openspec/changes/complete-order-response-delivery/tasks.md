# Implementation plan

Implemented under user approval. Scope: the retained eight-line EDIFACT core example and a separately sourced B&Q TRADACOMS ACK v1.4 full-acceptance simulator profile. Screwfix is excluded; Ocado ACK and production partner certification are not enabled. Prerequisite framework: `validate-outbound-readiness`.

## 1. Source and route contract

- [x] 1.1 Confirm the intended partner and first delivery route from retained provenance and current configuration.
- [x] 1.2 Pin the linked corrected source version/hash and capture fresh readiness findings.
- [x] 1.3 Group the recorded occurrences into field families and build the source-to-final-EDI disposition ledger.
- [x] 1.4 Obtain and record authoritative Order Response definitions and applicable partner guides, including version and usage rights.
- [x] 1.5 Identify unresolved business decisions and unsupported required target semantics; keep them explicit blockers.

## 2. Standards model, encoding and validation

- [x] 2.1 Define the Order Response target contracts in the standards modules and migrate this slice's message-specific encoding out of Integration.
- [x] 2.2 Implement header/line response semantics, identifiers/qualifiers, party details and all required repeated-line values.
- [x] 2.3 Implement base-standard validation for models and final bytes, plus separately versioned partner-profile rules.
- [x] 2.4 Audit accepted versus emitted fields; reject hidden losses, fabricated business defaults and unsupported values.
- [x] 2.5 Add independently reviewed valid/invalid fixtures for codes, conditional rules, numbers, dates, lengths, escaping and controls.
- [x] 2.6 Verify every supported field's output value; cover 1, 8, 12 and bounded maximum line counts, and reject empty documents where prohibited.

## 3. Replacement mappings and qualification

- [x] 3.1 Create replacement immutable mapping revisions and record all explicit dispositions without changing historical mappings.
- [x] 3.2 Require source coverage, required-target coverage and passing target/profile validation fixtures before publication.
- [x] 3.3 Add exact mapping/profile/target-rule evidence to route qualification and invalidate stale evidence.
- [x] 3.4 Publish, qualify and activate only the intended local agreement revision; keep other agreement pins intact.
- [x] 3.5 Confirm the actual corrected eight-line source passes readiness under the replacement route.

## 4. Recovery experience and API contracts

- [x] 4.1 Group repeated findings into business fields with line numbers/counts and expandable technical evidence.
- [x] 4.2 Let administrators create/test a draft revision against the exact authorized retained source without manually reconstructing input.
- [x] 4.3 Export changed contracts, regenerate SDKs and use authenticated BFFs for any new UI integration.
- [x] 4.4 Verify member/admin boundaries and tenant isolation for findings, retained test input, publication and activation.

## 5. Delivery proof and local fixtures

- [x] 5.1 Add stable valid, blocked and receipt-failure/retry fixtures with creation, repair and idempotence coverage.
- [x] 5.2 Deliver the actual eight-line corrected document to the AS2 simulator and verify its complete business values and retained payload hash.
- [x] 5.3 Verify configured MDN handling and the resulting Exchange delivery state, keeping transport receipt separate from business acceptance.
- [x] 5.4 Verify original/correction/generated/transfer/receipt lineage and ordered, numbered activity on desktop and mobile.
- [x] 5.5 Exercise invalid content, unsupported additions, stale dependencies, negative receipts, timeout, retry, idempotent replay and neighbouring-exchange isolation.
- [x] 5.6 Run affected standards/domain/API tests, contract/SDK checks, web checks and authenticated browser verification; record evidence and remaining dependencies.
- [x] 5.7 Update the local coverage manifest and reconcile scope/status with the broader six-document standards proposal.

See [verification evidence and explicit limits](verification.md). The optional final post-delivery B&Q restart check was interrupted; the earlier EDIFACT restart/replay and durable counter checks passed.

## Subsequent production coverage

- [ ] 6.1 Apply the same independently sourced validation, complete mappings and qualification gates to the remaining five document types in `add-complete-six-document-edi-support`.
- [ ] 6.2 Qualify each actual production partner/release/profile before enabling its route; do not treat simulator success as partner certification.
