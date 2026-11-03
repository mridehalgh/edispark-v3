# Trading network and document exchange

## Why

Documents and AS2 transport are independently useful, but an EDI operator needs an explicit, versioned contract that states which business partner accepts which document, standard, release, mapping, and transport connection. Keeping that policy in MFT would couple business semantics to protocol delivery and make partner-specific formats unsafe to evolve.

## What changes

- Add Trading Partnerships, Transformations, and Exchange bounded contexts behind anti-corruption layers.
- Add versioned and strictly qualified partner agreements for EDIFACT D96A and TRADACOMS Order/Invoice routes.
- Add constrained visual mappings with fixtures and immutable publication.
- Add outbound orchestration from a Documents version through generated EDI retention to AS2 submission with end-to-end provenance and idempotency.
- Add a generated Integration API client, authenticated BFF, Trading Network UI, and document-level send action.
- Add tenant-partitioned DynamoDB persistence and EventBridge-to-SQS agreement snapshot infrastructure.
- Split inbound EDIFACT and TRADACOMS files into independently routed messages, with partner-configurable strict or keep-valid failure handling and reviewable per-message evidence.

## Boundaries

MFT continues to own AS2 certificates, protocol policy, retries, payloads, and MDNs. Documents continues to own canonical business documents and retained representations. Arbitrary user code in mappings and silent fallback to an unqualified route are not permitted.
