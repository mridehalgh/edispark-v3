# Pagination support

EdiSpark cursor-paginated APIs use `Page`, `PaginatedResult` and `PaginatedResponse` so clients receive a consistent opaque-token contract.

All time-ordered paginated collections are returned from most recent to oldest. The business timestamp is followed by a stable identity tiebreaker so records cannot move unpredictably between page boundaries. Every domain method whose return signature contains `PaginatedResult` or `PaginatedResponse` must declare this contract with `@NewestFirstPagination` and must have behaviour coverage proving the ordering across pages.

The cross-domain ArchUnit suite in `tooling/architecture-tests` enforces the annotation and verifies that every Maven module under `domains/` participates in the scan. Adding a bounded context therefore requires adding its test dependency and base package to that suite before the build will pass.
