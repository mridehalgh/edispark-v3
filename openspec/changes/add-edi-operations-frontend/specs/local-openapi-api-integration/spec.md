## Introduction

This capability defines how the first frontend connects to the existing local backend without manually maintained endpoint wiring. The frontend must discover and use the local API contract so that operators see a live system, not a disconnected demo.

## Glossary

- **API_Explorer**: A frontend view that exposes the backend endpoint catalogue and request metadata for operators and developers.
- **Documents_API**: The existing Spring Boot API that exposes schema and document-set endpoints.
- **Frontend_Integration_Layer**: The client-side capability that loads backend metadata, executes API calls, and normalises backend responses for the frontend.
- **Local_Backend**: A locally running instance of the Spring Boot application using the repository's development defaults.
- **OpenAPI_Document**: The machine-readable API description served by the Local_Backend.

## ADDED Requirements

### Requirement 1: Discover the local backend contract automatically

**User Story:** As a developer or Operator, I want the frontend to connect to the Local_Backend automatically, so that I can run the UI locally without manually wiring every endpoint.

#### Acceptance Criteria

1. THE Frontend_Integration_Layer SHALL target the Local_Backend using repository-supported local development defaults.
2. WHEN the EDI_Operations_Workbench starts in a local environment, THE Frontend_Integration_Layer SHALL attempt to load the OpenAPI_Document from the Local_Backend.
3. IF the OpenAPI_Document cannot be loaded, THEN THE Frontend_Integration_Layer SHALL surface a connection failure state that identifies the expected Local_Backend location.
4. WHERE the Local_Backend location is overridden for development, THE Frontend_Integration_Layer SHALL use the overridden location for contract discovery and API requests.

### Requirement 2: Expose a live API catalogue from the OpenAPI contract

**User Story:** As an Operator, I want to inspect the live API contract from the frontend, so that I can understand which backend operations are available in the current environment.

#### Acceptance Criteria

1. WHEN the OpenAPI_Document is available, THE API_Explorer SHALL list the backend operations defined by the OpenAPI_Document.
2. WHEN an Operator opens an API operation in the API_Explorer, THE API_Explorer SHALL display the operation path, method, summary, and request or response schema metadata available from the OpenAPI_Document.
3. WHILE the Local_Backend is reachable, THE API_Explorer SHALL indicate that the displayed operation catalogue is sourced from the live OpenAPI_Document.
4. IF the OpenAPI_Document changes between frontend sessions, THEN THE API_Explorer SHALL reflect the changed operation catalogue in the next loaded session.

### Requirement 3: Use live backend responses in workflow views

**User Story:** As an Operator, I want workflow views to use live backend responses, so that every dashboard, form, and detail page reflects the running Local_Backend rather than static fixture data.

#### Acceptance Criteria

1. THE Frontend_Integration_Layer SHALL source schema, document-set, document version, derivative, validation, and content retrieval data from the Documents_API.
2. WHEN a workflow action completes successfully, THE Frontend_Integration_Layer SHALL return the backend response payload to the initiating Workflow_View.
3. IF the Documents_API returns an error response, THEN THE Frontend_Integration_Layer SHALL make the returned status and message details available to the initiating Workflow_View.
4. WHILE the Local_Backend is unreachable, THE Frontend_Integration_Layer SHALL prevent live workflow submission and present a recoverable retry path.

### Requirement 4: Make local integration observable to the user

**User Story:** As an Operator, I want clear visibility into frontend-backend integration state, so that I can distinguish platform issues from data-entry mistakes.

#### Acceptance Criteria

1. THE EDI_Operations_Workbench SHALL display whether the Local_Backend contract load is pending, successful, or failed.
2. WHEN a frontend view depends on a backend operation, THE EDI_Operations_Workbench SHALL show the request lifecycle state for that view.
3. IF a backend request fails after the OpenAPI_Document has loaded successfully, THEN THE EDI_Operations_Workbench SHALL identify the failed operation and provide a retry action.
4. WHEN the Local_Backend becomes reachable after a previous failure, THE EDI_Operations_Workbench SHALL allow the Operator to refresh the integration state without reloading the entire browser session.
