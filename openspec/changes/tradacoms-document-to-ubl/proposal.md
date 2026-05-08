## Why

EdiSpark needs a first end-to-end inbound slice that proves we can accept a TRADACOMS business document, normalise it into a canonical representation, and surface the result through the existing Documents API. Delivering raw payload preservation alongside a generated UBL derivative gives downstream consumers a stable integration shape without losing the original trading-partner evidence.

## What Changes

- Add the first inbound processing flow for a TRADACOMS file so EdiSpark can receive, classify, and persist it as a document in the existing documents domain.
- Generate a UBL representation from the stored TRADACOMS source document as a managed derivative of the source version.
- Expose both the raw TRADACOMS source and the generated UBL derivative through the existing document set, document, version, and derivative API flows.
- Establish the minimum metadata and content handling rules needed to distinguish source EDI content from derived UBL content.

## Capabilities

### New Capabilities
- `tradacoms-to-ubl-documents`: Ingest a TRADACOMS document, retain the raw source content, derive UBL content, and make both available through the existing documents API model.

### Modified Capabilities
None.

## Impact

- Affected code: inbound file processing, TRADACOMS parsing or mapping, documents application and domain layers, document persistence, and document API DTO or controller mappings.
- APIs: existing document retrieval and derivative discovery flows will be used to surface source TRADACOMS and derived UBL content; no separate public API family is introduced in this slice.
- Dependencies and assets: TRADACOMS parsing support, UBL generation or mapping logic, and content storage for both source and derivative payloads.
- Systems: inbound processing pipeline, document persistence, and downstream consumers that read document content from the Documents API.
