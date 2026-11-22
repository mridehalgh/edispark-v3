## ADDED Requirements

### Requirement: Published reference data is tenant-scoped and immutable

A lookup consumer SHALL resolve an exact published revision under the active tenant and SHALL report a missing required key. A newer revision SHALL leave an older route pin resolvable.

#### Scenario: A retailer mapping is reused by two tenants

- **WHEN** each tenant binds a different table to the same retailer mapping
- **THEN** each exchange resolves its own depot data and a foreign revision is unavailable

### Requirement: Tesco depot enrichment

An inbound Tesco order with a bound depot revision SHALL retain the retailer depot ID and enrich its delivery location using the matching row. An unbound existing route SHALL preserve its existing behaviour.
