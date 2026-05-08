## Why

EdiSpark needs a first end-to-end inbound slice that proves we can accept a TRADACOMS business document, validate and parse it, and surface the stored source document through the existing Documents API. This gives us a thin but complete path from inbound file receipt to document visibility while deferring UBL conversion until the raw ingestion path is stable.

## What Changes

- Add the first inbound processing flow for a TRADACOMS file so EdiSpark can receive, classify, parse, and persist it as a source document in the existing documents domain.
- Expose the raw TRADACOMS source document through the existing document set, document, version, and content API flows.
- Establish the minimum parsing metadata needed to distinguish successfully parsed TRADACOMS source content from rejected or unsupported payloads.
- Explicitly defer UBL generation and derivative handling from this first slice.

## Capabilities

### New Capabilities
- `tradacoms-documents-ingestion`: Ingest a TRADACOMS document, retain the raw source content, record parsing results, and make the source document available through the existing documents API model.

### Modified Capabilities
None.

## Impact

- Affected code: inbound file processing, TRADACOMS parsing, documents application and domain layers, document persistence, and document API DTO or controller mappings.
- APIs: existing document retrieval flows will surface source TRADACOMS metadata and source content; no derivative or UBL-specific API behaviour is introduced in this slice.
- Dependencies and assets: TRADACOMS parsing support and content storage for source payloads.
- Systems: inbound processing pipeline, document persistence, and downstream consumers that read source document content from the Documents API.
