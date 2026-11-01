# Global mapping catalogue requirements

## Catalogue ownership

The system SHALL vend immutable, versioned global mapping revisions owned by EdiSpark without storing a copy in each tenant partition. Every tenant SHALL be able to list, inspect, test, and select a published global revision.

## Default coverage

The catalogue SHALL include inbound and outbound Order and Invoice mappings for UBL 2.1 with EDIFACT D96A and TRADACOMS 9. Stable revision IDs SHALL remain suitable for partner agreement references.

## Samples

Every global mapping SHALL include comprehensive, privacy-safe parsed source and target examples covering representative headers, references, parties, terms, totals, identifiers, classifications, and repeated lines. Where either side is EDI, the mapping SHALL also include the corresponding complete raw EDI interchange rather than a fragment. The mapping workspace SHALL make these examples visible and load the source into the test bench.

## Tenant customization

Creating a revision from a global mapping SHALL produce a tenant-owned editable draft with lineage to the source global revision. Global revisions SHALL NOT be edited, archived, or deleted by tenants.

## Execution

Agreement qualification and document exchange SHALL resolve both tenant-owned and global published mappings. Inbound UBL target generation SHALL preserve JSON array structure represented by numeric JSON Pointer segments.
