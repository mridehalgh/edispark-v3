# Configuration lifecycle requirements

## Versioning

The system SHALL retain a stable family identity and monotonically increasing revision number for every partner, agreement, mapping, and AS2 profile. Published or active revisions SHALL be immutable.

## Promotion

The system SHALL support immediate and scheduled promotion. Due transitions SHALL be idempotent and SHALL revalidate dependencies before becoming live.

## Deletion and retention

The system SHALL permanently delete only unused drafts. If a configuration is live, scheduled, published, assigned, or historically evidenced, the system SHALL preserve it and return a dependency conflict for destructive deletion.

## Mapping editor

The UI SHALL expose synchronized visual and structured representations of one mapping graph. Every mapping operation SHALL remain usable without a pointing device.

## Certificates

Certificate material SHALL be immutable. Unused uploads MAY be deleted; assigned or historical certificates SHALL be retained and retired through rotation.
