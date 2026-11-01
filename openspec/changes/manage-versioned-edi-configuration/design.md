# Design

Configuration is represented by a stable `familyId` and an immutable revision `id`. Existing records default `familyId` to their current ID. Drafts may be edited in place with an optimistic `version`; promotion freezes the revision. A future `effectiveAt` creates a scheduled revision, while immediate promotion activates or publishes it directly.

Hard deletion is restricted to drafts that cannot be selected by qualified agreements. Published, active, scheduled, or evidenced records are retained and may instead be archived, suspended, superseded, or retired. API conflicts return a stable code and dependency details suitable for the UI.

The mapping graph remains the canonical representation. The structured builder and canvas are projections over the same ordered node collection, so switching views never creates two sources of truth. The structured view remains fully keyboard accessible.

Certificate bytes are never editable. Upload creates immutable material; AS2 profile revisions select certificate IDs and certificate rotation retains the prior signing certificate for the configured grace period.

