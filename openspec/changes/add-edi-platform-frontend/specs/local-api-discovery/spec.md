## Introduction

This capability defines how the frontend discovers and connects to the local backend automatically. The goal is to make local development and evaluation zero-friction by deriving the frontend's endpoint catalogue and connection state from the backend's OpenAPI surface.

## Glossary

- **Frontend_Discovery_Service**: The frontend capability that resolves backend connection details and endpoint metadata.
- **OpenAPI_Document**: The backend API description exposed for local development.
- **Local_Backend**: The backend service running on a developer workstation for the current environment.
- **Endpoint_Catalogue**: The frontend representation of available backend operations and resource groupings.
- **Connection_State**: The frontend status that indicates whether the Local_Backend is reachable and usable.

## ADDED Requirements

### Requirement 1: Automatic local backend discovery

**User Story:** As a developer, I want the frontend to discover the local backend automatically, so that I can start the UI without hand-configuring every endpoint.

#### Acceptance Criteria

1. WHEN the EDI_Operations_Frontend starts in a local environment, THE Frontend_Discovery_Service SHALL attempt to connect to the Local_Backend automatically.
2. WHEN the Local_Backend publishes an OpenAPI_Document, THE Frontend_Discovery_Service SHALL use the OpenAPI_Document as the contract source for local endpoint discovery.
3. WHERE a local backend origin is configured explicitly, THE Frontend_Discovery_Service SHALL prefer the configured origin over inferred defaults.
4. IF the Local_Backend cannot be reached, THEN THE Frontend_Discovery_Service SHALL set the Connection_State to a degraded local mode and report the failed backend origin.

### Requirement 2: Endpoint catalogue generation

**User Story:** As a developer, I want the frontend to organise discovered backend operations, so that the UI can stay aligned with the running API.

#### Acceptance Criteria

1. WHEN the OpenAPI_Document is available, THE Frontend_Discovery_Service SHALL derive an Endpoint_Catalogue from the published operations.
2. THE Frontend_Discovery_Service SHALL group the Endpoint_Catalogue into operator-facing categories that include document sets, documents, schemas, derivatives, validation, and API reference.
3. WHEN the OpenAPI_Document changes between frontend sessions, THE Frontend_Discovery_Service SHALL refresh the Endpoint_Catalogue from the latest published contract.
4. IF the OpenAPI_Document omits an expected operation, THEN THE Frontend_Discovery_Service SHALL mark the related UI surface as unavailable instead of presenting a broken action.

### Requirement 3: Connection transparency and fallback behaviour

**User Story:** As a local user, I want to understand what the frontend discovered and what is unavailable, so that I can troubleshoot configuration issues quickly.

#### Acceptance Criteria

1. THE EDI_Operations_Frontend SHALL present the current Connection_State in a visible interface location.
2. WHEN the Connection_State is healthy, THE EDI_Operations_Frontend SHALL identify the active OpenAPI_Document source and backend origin.
3. IF automatic discovery fails, THEN THE EDI_Operations_Frontend SHALL present recovery guidance that includes the expected local backend endpoint.
4. WHILE the Connection_State is degraded, THE EDI_Operations_Frontend SHALL keep non-dependent UI surfaces available and disable only operations that require the Local_Backend.

### Requirement 4: Consistent local development contract

**User Story:** As a product engineer, I want local autowiring to behave consistently across machines, so that frontend onboarding is predictable.

#### Acceptance Criteria

1. THE Frontend_Discovery_Service SHALL support a documented default local backend origin.
2. THE Frontend_Discovery_Service SHALL support environment-based override of the local backend origin.
3. WHEN the Local_Backend is reachable at the documented default origin, THE Frontend_Discovery_Service SHALL connect without requiring manual endpoint entry.
4. IF the configured backend origin serves an invalid OpenAPI_Document, THEN THE Frontend_Discovery_Service SHALL reject the contract and report the contract failure to the user.
