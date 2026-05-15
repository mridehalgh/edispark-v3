## Why

EdiSpark has a working backend API surface but no first-party frontend for exploring and exercising it during local development. A small shadcn-based frontend would give developers and analysts a usable entry point for the existing endpoints, reduce dependence on raw Swagger interactions, and establish the baseline UI architecture for future product screens.

## What Changes

- Add the first frontend module to the repository using a modern React stack with shadcn/ui components.
- Introduce an API explorer experience that reads the existing backend OpenAPI document and presents the currently available endpoints in a browsable UI.
- Provide endpoint-specific views for listing operations, inspecting request and response shapes, and invoking supported endpoints against a locally running backend.
- Automatically wire local development so the frontend targets the existing Spring Boot API and OpenAPI document without manual per-developer setup.
- Establish the minimum frontend build, run, and local integration conventions needed to extend this UI beyond the initial explorer.

## Capabilities

### New Capabilities
- `api-explorer-frontend`: A shadcn-based frontend that surfaces the current Documents and Schemas API endpoints, renders endpoint details from the local OpenAPI document, and invokes the local backend through a developer-friendly UI.

### Modified Capabilities
None.

## Impact

- Affected code: new frontend module, local development configuration, and backend integration settings required for browser-based local access.
- APIs: no business API contract changes are required, but the frontend will depend on the existing `/api-docs` and REST endpoints remaining available locally.
- Dependencies: frontend runtime and build tooling, shadcn/ui component setup, and an OpenAPI-driven client or schema ingestion approach.
- Systems: local developer workflow will expand from backend-only execution to a coordinated backend plus frontend experience.
