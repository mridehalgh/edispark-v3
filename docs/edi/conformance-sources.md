# EDI conformance sources

The repository is the executable source of truth for EdiSpark's supported EDI profiles. Conformance claims must be
bounded to the checked-in parsers, schemas, message profiles and deterministic fixtures described below.

## EDIFACT D96A

The `standards/edifact` module owns D96A syntax parsing and writing. The global mappings and MFT fixtures cover
`ORDERS`, `ORDRSP`, `DESADV`, `INVOIC`, `REMADV` and `RECADV`; their lifecycle round-trip contract is enforced by
`UblEdiLifecycleE2ETest`.

## TRADACOMS version 9

The checked-in `standards/tradacoms` module owns the parser, writer, schema model, validation model and repository
message profiles. `CREDIT.json`, `tradacoms.txt`, parser property tests, global mappings and lifecycle fixtures are
maintained together. No external Maven artifact is required to build or exercise the supported local profile.

## Supported lifecycle contract

Both standards support Order, Order Response, Despatch Advice, Invoice, Remittance Advice and Receipt Advice in both
mapping directions. The local trading agreements use the business lifecycle directions, while the global catalogue
keeps the reverse mappings available for partner-specific agreements. Tests assert message identity, release,
document identity, interchange containment, repair and idempotent sample reconciliation.
