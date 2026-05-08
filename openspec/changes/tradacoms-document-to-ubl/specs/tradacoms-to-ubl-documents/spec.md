## Introduction

This capability defines the first inbound processing slice for TRADACOMS business documents. The slice stores the original TRADACOMS payload as the source business document, generates a UBL representation as a derivative, and makes both artefacts available through the existing Documents API structure.

## Glossary

- **Documents_API**: The existing EdiSpark API for managing document sets, documents, versions, derivatives, and associated content.
- **Inbound_TRADACOMS_Slice**: The EdiSpark processing flow that receives a TRADACOMS file and registers it in the documents domain.
- **Source_TRADACOMS_Document**: The persisted original TRADACOMS payload captured exactly as received for a supported inbound business message.
- **Canonical_Document_Model**: The internal business representation used to map supported TRADACOMS content into UBL.
- **UBL_Derivative**: The UBL serialisation generated from a stored Source_TRADACOMS_Document and linked to the originating document version.
- **Supported_TRADACOMS_Message**: A TRADACOMS message type and structure that this first slice explicitly maps into the Canonical_Document_Model.

## ADDED Requirements

### Requirement 1: Register raw TRADACOMS source content

**User Story:** As an integration analyst, I want inbound TRADACOMS files stored as source documents, so that EdiSpark preserves the original trading-partner payload.

#### Acceptance Criteria

1. WHEN the Inbound_TRADACOMS_Slice receives a Supported_TRADACOMS_Message, THE Inbound_TRADACOMS_Slice SHALL create a document set and a source document version for the received business document.
2. WHEN the Inbound_TRADACOMS_Slice stores a Source_TRADACOMS_Document, THE Inbound_TRADACOMS_Slice SHALL preserve the raw payload bytes exactly as received.
3. WHEN the Documents_API returns metadata for a Source_TRADACOMS_Document, THE Documents_API SHALL identify the source version as EDI content.

### Requirement 2: Translate supported TRADACOMS content into UBL

**User Story:** As a platform developer, I want a UBL representation generated from supported TRADACOMS content, so that downstream integrations can consume a canonical XML business document.

#### Acceptance Criteria

1. WHEN a Source_TRADACOMS_Document is created from a Supported_TRADACOMS_Message, THE Inbound_TRADACOMS_Slice SHALL generate one UBL_Derivative for the stored source version.
2. WHEN the Inbound_TRADACOMS_Slice generates a UBL_Derivative, THE Inbound_TRADACOMS_Slice SHALL link the UBL_Derivative to the source document version from which it was produced.
3. THE Inbound_TRADACOMS_Slice SHALL serialise each UBL_Derivative as XML content.
4. THE Inbound_TRADACOMS_Slice SHALL ensure that parsing a Supported_TRADACOMS_Message into the Canonical_Document_Model, serialising the Canonical_Document_Model to UBL, and reparsing the UBL output yields an equivalent Canonical_Document_Model for the mapped business fields.

### Requirement 3: Expose source and derivative metadata through the Documents API

**User Story:** As an operations engineer, I want the existing Documents API to show both the original TRADACOMS document and its UBL derivative, so that I can inspect what was received and what was produced.

#### Acceptance Criteria

1. WHEN a client retrieves a document created by the Inbound_TRADACOMS_Slice, THE Documents_API SHALL include metadata for the current source version and all derivatives associated with that document.
2. WHEN a client lists derivatives for a document created by the Inbound_TRADACOMS_Slice, THE Documents_API SHALL include the UBL_Derivative in the derivative collection.
3. WHEN a client retrieves UBL_Derivative metadata through the Documents_API, THE Documents_API SHALL identify the derivative transformation as TRADACOMS-to-UBL.

### Requirement 4: Expose raw and UBL content through the Documents API

**User Story:** As a downstream consumer, I want to download both the raw TRADACOMS payload and the generated UBL document from the Documents API, so that I can choose the representation that fits my integration.

#### Acceptance Criteria

1. WHEN a client requests the content of a source version created by the Inbound_TRADACOMS_Slice, THE Documents_API SHALL return the stored Source_TRADACOMS_Document payload.
2. WHEN a client requests the content of a UBL_Derivative, THE Documents_API SHALL return the stored UBL_Derivative payload.
3. WHEN the Documents_API returns Source_TRADACOMS_Document content, THE Documents_API SHALL identify the returned content format as EDI.
4. WHEN the Documents_API returns UBL_Derivative content, THE Documents_API SHALL identify the returned content format as XML.

### Requirement 5: Preserve source evidence when translation fails

**User Story:** As an operations engineer, I want unsupported or unmappable TRADACOMS inputs to retain the raw source document, so that document evidence is not lost when UBL generation cannot complete.

#### Acceptance Criteria

1. IF the Inbound_TRADACOMS_Slice cannot map a received TRADACOMS payload into the Canonical_Document_Model, THEN THE Inbound_TRADACOMS_Slice SHALL preserve the Source_TRADACOMS_Document.
2. IF the Inbound_TRADACOMS_Slice cannot generate a UBL_Derivative for a stored Source_TRADACOMS_Document, THEN THE Inbound_TRADACOMS_Slice SHALL record the translation failure against the document processing result.
3. IF no UBL_Derivative exists for a stored Source_TRADACOMS_Document, THEN THE Documents_API SHALL return source document metadata without reporting a successful UBL derivative.
