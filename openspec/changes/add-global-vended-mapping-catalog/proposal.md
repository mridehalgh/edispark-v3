# Global EdiSpark mapping catalogue

## Why

Integration analysts need safe, usable defaults for common UBL, EDIFACT, and TRADACOMS exchanges without recreating mappings and representative fixtures for every tenant.

## What changes

- Vend eight immutable, versioned EdiSpark global mappings for both directions of Order and Invoice across UBL 2.1, EDIFACT D96A, and TRADACOMS 9.
- Attach privacy-safe parsed source and target documents plus the applicable raw EDI interchange to every global revision.
- Allow agreements to use published global revisions directly and allow analysts to fork a global mapping into a tenant-owned draft.
- Show global ownership, samples, raw EDI, parsed JSON, and testing controls in the mapping library and workspace.

## Impact

The transformations domain, Integration API contract, generated TypeScript client, local demo catalogue, and mapping UI change additively. Existing deterministic mapping IDs remain stable.
