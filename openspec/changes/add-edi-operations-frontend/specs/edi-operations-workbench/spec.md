## Introduction

This capability defines the first operator-facing frontend for the EdiSpark platform. The workbench presents the existing schema and document APIs through a retail-oriented B2B EDI experience so users can understand, create, inspect, validate, and download business documents without using Swagger directly.

## Glossary

- **Documents_API**: The existing Spring Boot API that exposes schema and document-set endpoints.
- **EDI_Operations_Workbench**: The frontend application that provides the first browser-based user experience for the platform.
- **Operator**: A user who manages schemas, document sets, document versions, validation results, and related document content.
- **Retail_Journey**: A curated view of a retail EDI flow such as purchase order, despatch advice, receipt advice, or invoice handling.
- **Workflow_View**: A screen that groups API operations into a task-oriented experience rather than an endpoint list.

## ADDED Requirements

### Requirement 1: Provide a task-oriented frontend shell

**User Story:** As an Operator, I want a clear frontend shell for EDI operations, so that I can move between retail workflows and API-backed document tasks without using raw endpoints.

#### Acceptance Criteria

1. THE EDI_Operations_Workbench SHALL provide persistent primary navigation for dashboard, schemas, document sets, retail journeys, and API explorer views.
2. WHEN an Operator opens the EDI_Operations_Workbench, THE EDI_Operations_Workbench SHALL display the current backend connection state within the global shell.
3. WHILE an Operator navigates between views, THE EDI_Operations_Workbench SHALL preserve a consistent page header, action area, and status region.
4. IF the EDI_Operations_Workbench cannot load its initial application state, THEN THE EDI_Operations_Workbench SHALL show an error state with a retry action.

### Requirement 2: Present retail-oriented workflow context

**User Story:** As an Operator, I want the frontend to reflect common UK retail EDI journeys, so that the platform feels aligned to purchase order, shipment, and invoicing operations rather than generic document storage.

#### Acceptance Criteria

1. THE EDI_Operations_Workbench SHALL present Retail_Journeys for purchase order, despatch advice, receipt advice, and invoice handling.
2. WHEN an Operator opens a Retail_Journey, THE EDI_Operations_Workbench SHALL show the related business document types, recommended next actions, and linked API-backed records.
3. WHILE a Retail_Journey contains no matching backend records, THE EDI_Operations_Workbench SHALL present an empty state that explains which records can populate the Retail_Journey.
4. WHERE a backend capability required for a Retail_Journey does not yet exist, THE EDI_Operations_Workbench SHALL mark that journey step as planned rather than executable.

### Requirement 3: Support schema operations through forms and detail views

**User Story:** As an Operator, I want guided schema management screens, so that I can create schemas and versions without composing JSON requests manually.

#### Acceptance Criteria

1. THE EDI_Operations_Workbench SHALL provide a Workflow_View for creating a schema and adding a schema version.
2. WHEN an Operator enters schema creation data, THE EDI_Operations_Workbench SHALL validate required fields before submission.
3. WHEN the Documents_API returns a created schema or schema version, THE EDI_Operations_Workbench SHALL display the returned identifiers and version metadata.
4. IF the Documents_API rejects a schema operation, THEN THE EDI_Operations_Workbench SHALL display the returned error details without losing the Operator's entered values.

### Requirement 4: Support document-set lifecycle operations through guided workflows

**User Story:** As an Operator, I want guided document-set workflows, so that I can create a document set, attach documents, add versions, and create derivatives from the browser.

#### Acceptance Criteria

1. THE EDI_Operations_Workbench SHALL provide Workflow_Views for listing document sets, creating a document set, adding a document, adding a document version, and creating a derivative.
2. WHEN the Documents_API returns paginated document-set results, THE EDI_Operations_Workbench SHALL expose continuation navigation using the pagination data returned by the Documents_API.
3. WHEN an Operator opens a document set, THE EDI_Operations_Workbench SHALL display the documents, current versions, available derivatives, and related schema references for that document set.
4. IF an Operator submits invalid or incomplete document content, THEN THE EDI_Operations_Workbench SHALL prevent submission or display the validation failure returned by the Documents_API.

### Requirement 5: Expose document inspection and validation outcomes

**User Story:** As an Operator, I want rich inspection views for validation and content retrieval, so that I can troubleshoot EDI payloads and document history from one place.

#### Acceptance Criteria

1. THE EDI_Operations_Workbench SHALL provide a document version detail view that shows version metadata, validation status, and available actions.
2. WHEN an Operator requests document validation, THE EDI_Operations_Workbench SHALL display the validation outcome returned by the Documents_API, including pass or fail status and message details.
3. WHEN an Operator requests raw document content, THE EDI_Operations_Workbench SHALL provide a browser-initiated download using the content returned by the Documents_API.
4. IF raw document content is unavailable, THEN THE EDI_Operations_Workbench SHALL display a recoverable error state that identifies the affected document version.
