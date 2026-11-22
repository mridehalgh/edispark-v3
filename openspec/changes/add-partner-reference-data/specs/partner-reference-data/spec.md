## ADDED Requirements

### Requirement: Published reference data is tenant-scoped and immutable

A lookup consumer SHALL resolve an exact published revision under the active tenant and SHALL report a missing required key. A newer revision SHALL leave an older route pin resolvable.

#### Scenario: A partner mapping is reused by two tenants

- **WHEN** each tenant binds a different table to the same partner mapping
- **THEN** each exchange resolves its own reference data and a foreign revision is unavailable

### Requirement: Configurable mapping lookups

A mapping revision SHALL declare each lookup's source field, table alias, result column and target field. A route SHALL resolve those aliases using tenant-scoped immutable table revisions.

#### Scenario: A mapping translates an external code

- **WHEN** an input field contains a matching key in the pinned table
- **THEN** the mapping writes the requested column to the configured target field and records the table revision in its report
