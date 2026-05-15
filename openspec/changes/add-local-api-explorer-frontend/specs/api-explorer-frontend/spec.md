## Introduction

This capability defines the first EdiSpark frontend. The frontend presents the existing backend API through a shadcn-based explorer that loads the local OpenAPI document, shows the available operations, and lets a developer exercise supported endpoints against a locally running backend.

## Glossary

- **Api_Explorer_Frontend**: The first EdiSpark browser application for viewing and invoking backend API operations.
- **Endpoint_Detail_View**: The frontend view that shows the selected operation summary, path, method, request inputs, and latest response.
- **Endpoint_Operation**: A single REST operation described by the local OpenAPI document, including HTTP method, path, tags, parameters, and request or response schemas.
- **Local_Backend**: The locally running Spring Boot application that exposes the existing REST endpoints and OpenAPI document.
- **Local_OpenAPI_Document**: The OpenAPI description served by the Local_Backend for local development.

## ADDED Requirements

### Requirement 1: Load the local API description

**User Story:** As a developer, I want the frontend to discover the local API definition automatically, so that I can start the UI without manual endpoint configuration.

#### Acceptance Criteria

1. WHEN the Api_Explorer_Frontend starts in local development, THE Api_Explorer_Frontend SHALL request the Local_OpenAPI_Document from the configured Local_Backend.
2. WHEN the Local_OpenAPI_Document is available, THE Api_Explorer_Frontend SHALL render the documented Endpoint_Operation entries without requiring a developer to edit frontend source code or local configuration files.
3. IF the Local_OpenAPI_Document cannot be retrieved, THEN THE Api_Explorer_Frontend SHALL show an error state that identifies the failed document load and preserves the rest of the application shell.

### Requirement 2: Present a browsable endpoint catalogue

**User Story:** As an integration analyst, I want to browse the available API operations, so that I can understand which backend capabilities already exist.

#### Acceptance Criteria

1. WHEN the Api_Explorer_Frontend loads the Local_OpenAPI_Document, THE Api_Explorer_Frontend SHALL present each Endpoint_Operation with its HTTP method, request path, and OpenAPI summary or operation identifier.
2. WHEN the Local_OpenAPI_Document contains operation tags, THE Api_Explorer_Frontend SHALL group Endpoint_Operation entries by tag.
3. WHILE a developer is browsing the catalogue, THE Api_Explorer_Frontend SHALL keep the selected Endpoint_Operation visually distinct from the remaining entries.

### Requirement 3: Show endpoint-specific details

**User Story:** As a platform developer, I want a detailed view for each operation, so that I can inspect how to call the backend correctly.

#### Acceptance Criteria

1. WHEN a developer selects an Endpoint_Operation, THE Api_Explorer_Frontend SHALL open an Endpoint_Detail_View for the selected Endpoint_Operation.
2. WHEN the Endpoint_Detail_View is shown, THE Api_Explorer_Frontend SHALL display the selected Endpoint_Operation method, path, summary or description, and documented request inputs.
3. WHEN the Endpoint_Detail_View is shown, THE Api_Explorer_Frontend SHALL display the documented response status codes for the selected Endpoint_Operation.

### Requirement 4: Invoke local backend operations

**User Story:** As a developer, I want to submit requests from the frontend, so that I can exercise the existing API without leaving the application.

#### Acceptance Criteria

1. WHEN a developer submits supported request inputs from an Endpoint_Detail_View, THE Api_Explorer_Frontend SHALL send the request to the Local_Backend using the selected Endpoint_Operation method and path.
2. WHEN the Local_Backend returns a response, THE Api_Explorer_Frontend SHALL display the returned HTTP status and response body in the Endpoint_Detail_View.
3. IF the Local_Backend request fails because the browser cannot reach the Local_Backend, THEN THE Api_Explorer_Frontend SHALL display a request failure message for the selected Endpoint_Operation.

### Requirement 5: Support a reliable local developer loop

**User Story:** As a contributor, I want the frontend and backend to work together locally with minimal setup, so that I can extend the UI quickly.

#### Acceptance Criteria

1. WHEN a contributor starts the documented local frontend workflow, THE Api_Explorer_Frontend SHALL target the Local_Backend without requiring the contributor to modify endpoint URLs in application code.
2. WHERE the Local_Backend is available on the documented local address, THE Api_Explorer_Frontend SHALL allow browser requests to complete without cross-origin errors.
3. THE Api_Explorer_Frontend SHALL provide a documented local run path for loading the endpoint catalogue and invoking the existing document and schema operations.
