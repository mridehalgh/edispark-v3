# Design

Publish immutable reference table revisions under tenant ownership. The table declares its unique input column and preserves codes as exact strings. A mapping node chooses a source JSON pointer, a table alias, an output column and a target JSON pointer. The route binds aliases to published revisions. Mapping fixtures use the same lookup operator with explicit test bindings.

Inbound and outbound mappings use the same deterministic operator. Missing bindings, columns or keys return an actionable mapping finding. Publishing new data leaves existing route pins intact, and the mapping report records the revisions it resolved.

Extend the existing managed setup later with route-specific business inputs, readiness results and test-to-live progress. Workspace legal and tax facts belong to the organisation. Partner-specific account identifiers belong to the trading relationship. Connection and certificate settings remain owned by MFT.
