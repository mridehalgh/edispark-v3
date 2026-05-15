## Introduction

This capability defines the first inbound processing slice for TRADACOMS business documents. The slice stores the original TRADACOMS payload as the source business document, parses the payload to determine whether it is supported and structurally valid, and makes the stored source artefact available through the existing Documents API structure. UBL conversion is out of scope for this slice.

## Glossary

- **Documents_API**: The existing EdiSpark API for managing document sets, documents, versions, and associated content.
- **Inbound_TRADACOMS_Slice**: The EdiSpark processing flow that receives a TRADACOMS file and registers it in the documents domain.
- **Source_TRADACOMS_Document**: The persisted original TRADACOMS payload captured exactly as received for an inbound business message.
- **Supported_TRADACOMS_Message**: A TRADACOMS message type and structure that this first slice explicitly recognises and parses.
- **Tradacoms_Parse_Result**: The structured outcome of validating and parsing a Source_TRADACOMS_Document, including parse status, identified message type, and parse errors.

## ADDED Requirements

### Requirement 1: Register raw TRADACOMS source content

**User Story:** As an integration analyst, I want inbound TRADACOMS files stored as source documents, so that EdiSpark preserves the original trading-partner payload.

#### Acceptance Criteria

1. WHEN the Inbound_TRADACOMS_Slice receives a Supported_TRADACOMS_Message, THE Inbound_TRADACOMS_Slice SHALL create a document set and a source document version for the received business document.
2. WHEN the Inbound_TRADACOMS_Slice stores a Source_TRADACOMS_Document, THE Inbound_TRADACOMS_Slice SHALL preserve the raw payload bytes exactly as received.
3. WHEN the Documents_API returns metadata for a Source_TRADACOMS_Document, THE Documents_API SHALL identify the source version as EDI content.

### Requirement 2: Parse supported TRADACOMS content

**User Story:** As a platform developer, I want supported TRADACOMS content parsed into a structured result, so that EdiSpark can recognise what was received before later transformation slices are added.

#### Acceptance Criteria

1. WHEN the Inbound_TRADACOMS_Slice receives a Supported_TRADACOMS_Message, THE Inbound_TRADACOMS_Slice SHALL produce a Tradacoms_Parse_Result for the source payload.
2. WHEN parsing succeeds, THE Inbound_TRADACOMS_Slice SHALL record the identified TRADACOMS message type against the stored source document metadata.
3. IF the received payload is invalid or unsupported for the first slice, THEN THE Inbound_TRADACOMS_Slice SHALL record a non-success parse status and associated parse errors.

### Requirement 3: Expose source document metadata through the Documents API

**User Story:** As an operations engineer, I want the existing Documents API to show the stored TRADACOMS document and its parse outcome, so that I can inspect what was received and whether parsing succeeded.

#### Acceptance Criteria

1. WHEN a client retrieves a document created by the Inbound_TRADACOMS_Slice, THE Documents_API SHALL include metadata for the current source version.
2. WHEN the Documents_API returns metadata for a document created by the Inbound_TRADACOMS_Slice, THE Documents_API SHALL include the stored parse status.
3. WHEN the Documents_API returns metadata for a successfully parsed document, THE Documents_API SHALL include the identified TRADACOMS message type.

### Requirement 4: Expose raw source content through the Documents API

**User Story:** As a downstream consumer, I want to download the raw TRADACOMS payload from the Documents API, so that I can access the preserved trading-partner source document.

#### Acceptance Criteria

1. WHEN a client requests the content of a source version created by the Inbound_TRADACOMS_Slice, THE Documents_API SHALL return the stored Source_TRADACOMS_Document payload.
2. WHEN the Documents_API returns Source_TRADACOMS_Document content, THE Documents_API SHALL identify the returned content format as EDI.

### Requirement 5: Preserve source evidence when parsing fails

**User Story:** As an operations engineer, I want invalid or unsupported TRADACOMS inputs to retain the raw source document, so that document evidence is not lost when parsing cannot complete successfully.

#### Acceptance Criteria

1. IF the Inbound_TRADACOMS_Slice cannot successfully parse a received TRADACOMS payload, THEN THE Inbound_TRADACOMS_Slice SHALL preserve the Source_TRADACOMS_Document.
2. IF the Inbound_TRADACOMS_Slice cannot successfully parse a received TRADACOMS payload, THEN THE Inbound_TRADACOMS_Slice SHALL record the parse failure against the document processing result.
