# Complete qualified Order Response delivery

## Why

The validation/recovery framework now stops unsafe sends, but the affected Order Response still cannot be delivered: the retained mapping leaves 55 populated values uncovered. Existing tests prove repeated-line mechanics and transport encoding, not the complete business semantics of this document or a qualified partner delivery.

This change completes that journey while preserving the original source, failed exchanges and published mapping history. It is the first delivery slice of `add-complete-six-document-edi-support`; that broader standards backlog remains open.

## What changes

- Reproduce the findings against an exact corrected source version and intended partner route. Treat 55 as the last recorded count, not a fixed contract.
- Produce a field disposition ledger connecting source values, mapping rules and their representation in final EDI, including repeated lines and required target fields.
- Extend the standards modules' Order Response models, writers and validation using authoritative definitions and the applicable partner guide. Remove message-specific ownership from Integration for this slice.
- Publish replacement immutable mapping revisions with full fixture coverage; qualify a new agreement revision against the exact mapping, profile and target rules.
- Make remaining findings actionable for operations users and mapping administrators, with grouped repeated fields and access to the exact retained test input.
- Prove successful local AS2 delivery, receipt handling, document lineage and numbered activity using the actual affected eight-line document.

## Capabilities

### New capabilities

- `qualified-order-response-delivery`: A source-complete, target-validated and traceable Order Response can be delivered under an explicitly qualified partner route.

### Existing foundations

Reuse `validate-outbound-readiness`, the existing mapping revision workflow, generated SDKs, partner qualification and MFT receipt processing. Do not reimplement UBL schema validation or introduce another orchestration service.

## Scope and dependencies

Start with the affected Order Response and the configured EDIFACT D96A / TRADACOMS v9 demo counterparts. Confirm the intended partner and standard from retained provenance before selecting the first route; a failed attempt against a particular standard is not sufficient evidence of intent.

The repository still lists authoritative EDIFACT definitions and licensed TRADACOMS definitions as outstanding. Establish provenance, permission to use and exact versions before implementing or claiming conformance for each route. A route without adequate definitions remains explicitly unsupported; work on an independently specified route can continue.

Full coverage of Order, Invoice, Despatch Advice, Receipt Advice and Remittance Advice belongs to the existing six-document proposal. This milestone must not be described as completing that wider scope.

## Impact

Standards modules, Transformations, Partnerships qualification, Exchange readiness, Integration composition, MFT/simulator verification, generated contracts/clients where changed, Documents recovery UI and deterministic local fixtures. This is an implementation plan only; it does not publish mappings, change live agreements or send real partner traffic.
