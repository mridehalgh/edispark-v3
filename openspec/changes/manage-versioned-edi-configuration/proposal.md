# Versioned EDI configuration management

## Why

Integration analysts need to safely create, revise, schedule, archive, and remove trading configuration without invalidating live routes or historical exchange evidence.

## What changes

- Add stable configuration-family identities and numbered revisions to partners, agreements, mappings, and AS2 profiles.
- Keep published or activated revisions immutable and allow only unused drafts to be permanently deleted.
- Add immediate and scheduled promotion, usage inspection, archive/retire operations, and structured dependency conflicts.
- Add a synchronized structured and visual mapping editor.
- Treat certificate material as immutable and permit deletion only while unused.

## Impact

The Integration and MFT OpenAPI contracts, generated TypeScript clients, local demo fixtures, and Trading Network/MFT management interfaces change additively. Existing revision IDs and exchange evidence remain valid.

