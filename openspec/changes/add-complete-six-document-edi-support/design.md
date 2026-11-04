# Design

## Standards ownership

The EDIFACT and TRADACOMS standards modules own schema-aware parsing, validation, and writing. Transformations maps between UBL 2.1 JSON and typed EDI document models. Integration orchestrates these components but contains no message-specific segment formatting.

## Completeness contract

Each mapping revision carries a generated coverage ledger. Every addressable source and target field is classified as `EXACT`, `TRANSFORMED`, `DERIVED`, `DEFAULTED`, `EXTENSION_PRESERVED`, `SOURCE_ONLY`, or `NOT_APPLICABLE`, with a non-empty rationale for non-mapped dispositions. Contract checks fail when schemas drift or a field is unclassified.

Inbound values without a native UBL equivalent are stored in `UBLExtensions` under `urn:edispark:mapping-trace:1`. Outbound populated source-only values create warnings. The agreement capability either stops before transport (`STRICT`) or proceeds while retaining warnings (`ALLOW_WITH_WARNING`). Raw values remain in encrypted document content and are not copied into logs or warning metadata.

## Compatibility

Published global mapping IDs remain immutable. Complete Order and Invoice mappings receive new revision IDs in their existing families; new document families begin at revision 1. Existing agreements remain pinned until explicitly revised and qualified. Missing persisted loss policies resolve to `STRICT`.

