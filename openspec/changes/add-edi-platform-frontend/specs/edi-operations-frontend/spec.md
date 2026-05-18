## Introduction

This capability defines the first operator-facing frontend for EdiSpark. The frontend presents the existing local documents API as an EDI operations workbench with a UK retail feel, giving integration analysts and operations users a practical way to inspect, create, validate, and monitor EDI-related resources.

## Glossary

- **EDI_Operations_Frontend**: The browser-based frontend application for EDI operators.
- **Document_API**: The existing backend API that exposes document set, document, document version, schema, derivative, and validation resources.
- **Operator**: A user who configures, validates, or troubleshoots EDI document processing.
- **Message_Type**: A business document classification such as ORDERS, ORDRSP, DESADV, RECADV, INVOIC, REMADV, PAYORD, PRICAT, INVRPT, or SLSRPT.
- **Operational_View**: A frontend screen that exposes status, actions, and details for EDI resources.

## ADDED Requirements

### Requirement 1: EDI operations home

**User Story:** As an operator, I want a clear EDI operations home view, so that I can understand the platform scope and reach the most important workflows quickly.

#### Acceptance Criteria

1. THE EDI_Operations_Frontend SHALL provide an Operational_View that acts as the default landing page for local users.
2. THE EDI_Operations_Frontend SHALL present navigation for document sets, documents, schema management, validation workflows, and API exploration from the default landing page.
3. THE EDI_Operations_Frontend SHALL present the supported Message_Type values ORDERS, ORDRSP, DESADV, RECADV, INVOIC, REMADV, PAYORD, PRICAT, INVRPT, and SLSRPT as part of the operational context.
4. THE EDI_Operations_Frontend SHALL present the platform context as retail and e-commerce EDI processing rather than a generic file-storage experience.

### Requirement 2: Resource exploration and inspection

**User Story:** As an operator, I want to browse existing backend resources, so that I can inspect document state and troubleshoot API-backed flows without using Swagger directly.

#### Acceptance Criteria

1. WHEN the Operator opens a resource list view, THE EDI_Operations_Frontend SHALL display data returned by the Document_API for the selected resource type.
2. WHEN the Operator selects a document set, schema, document, document version, or derivative, THE EDI_Operations_Frontend SHALL display a detail Operational_View for the selected resource.
3. WHEN the Document_API returns paginated document-set results, THE EDI_Operations_Frontend SHALL provide pagination controls that use the backend continuation model.
4. IF the Document_API returns an error for a list or detail request, THEN THE EDI_Operations_Frontend SHALL show the error state, the affected resource, and a retry action in the active Operational_View.

### Requirement 3: Actionable creation and validation workflows

**User Story:** As an integration analyst, I want guided workflows for creating and validating EDI-backed resources, so that I can exercise the local platform without manually crafting raw API requests.

#### Acceptance Criteria

1. WHEN the Operator starts a create-schema, add-schema-version, create-document-set, add-document, add-version, create-derivative, or validate-document action, THE EDI_Operations_Frontend SHALL provide a task-specific input workflow for that action.
2. WHEN an action requires Base64-encoded content, THE EDI_Operations_Frontend SHALL accept a human-readable payload input and present the encoded request payload before submission.
3. WHEN a create or validation action completes successfully, THE EDI_Operations_Frontend SHALL present the resulting backend resource or validation result in the current workflow.
4. IF an action fails validation or backend processing, THEN THE EDI_Operations_Frontend SHALL present the returned error or validation outcome without discarding the Operator's submitted inputs.

### Requirement 4: Retail-oriented operational framing

**User Story:** As an operations user in retail and e-commerce, I want the frontend to reflect familiar B2B trading language, so that I can map generic backend resources to real EDI work.

#### Acceptance Criteria

1. THE EDI_Operations_Frontend SHALL label Operational_View content using EDI and retail language that distinguishes message intent, validation status, and document lineage.
2. WHEN the Operator views a document creation or inspection workflow, THE EDI_Operations_Frontend SHALL allow the Operator to associate the workflow with a supported Message_Type.
3. WHERE retail-oriented presentation is shown, THE EDI_Operations_Frontend SHALL preserve access to the underlying backend identifiers and raw resource details.
4. THE EDI_Operations_Frontend SHALL present a visual style suitable for B2B retail operations on desktop and mobile displays.

### Requirement 5: API visibility for operators

**User Story:** As a platform developer, I want the frontend to expose how UI actions map to backend operations, so that I can debug integration behaviour locally.

#### Acceptance Criteria

1. WHEN the Operator or platform developer inspects an action or detail view, THE EDI_Operations_Frontend SHALL expose the backend endpoint used for that interaction.
2. WHEN the Operator submits an action workflow, THE EDI_Operations_Frontend SHALL present the request and response payloads for local debugging.
3. WHILE the EDI_Operations_Frontend is connected to a local backend, THE EDI_Operations_Frontend SHALL identify the active backend origin in the interface.
