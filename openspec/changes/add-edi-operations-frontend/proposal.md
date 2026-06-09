## Why

The repository currently exposes a useful Documents API, but it has no operator-facing frontend for customers or internal teams to manage schemas, ingest business documents, inspect document versions, or validate EDI payloads without dropping to Swagger or direct HTTP calls. A first frontend is needed now so the platform can demonstrate a credible B2B EDI experience for UK retail and e-commerce users while staying wired to the real local API surface.

## What Changes

- Add a first web frontend using `shadcn/ui` with a polished UK retail and e-commerce operations aesthetic rather than a generic CRUD console.
- Introduce an EDI operations workbench that presents the existing backend capabilities as task-oriented views for schema management, document-set intake, document inspection, version history, derivative creation, validation results, and content download.
- Add a locally auto-wired API integration layer that discovers the running backend from the existing local Spring Boot configuration and consumes the OpenAPI document at `/api-docs` instead of relying on manually maintained endpoint mappings.
- Shape the UX around real retail EDI workflows such as `ORDERS`, `DESADV`, `RECADV`, and `INVOIC`, while mapping those journeys onto the current document and schema APIs until broader workflow endpoints exist.
- Define a frontend information architecture that leaves clear extension points for future multi-tenant dashboards, partner monitoring, acknowledgements, routing, and batch processing.

## Capabilities

### New Capabilities
- `edi-operations-workbench`: A shadcn-based frontend for browsing and operating the platform through dashboards, workflow views, forms, and detail screens aligned to B2B EDI document operations.
- `local-openapi-api-integration`: Automatic local integration between the frontend and the running Spring Boot backend using the live OpenAPI description and local environment defaults.

### Modified Capabilities
- None.

## Impact

- Adds a new frontend application to the repo, including React, shadcn/ui, routing, styling, and API client infrastructure.
- Uses the existing Spring Boot application endpoints under `/api/document-sets`, `/api/schemas`, and `/api-docs` as the initial data plane for the UI.
- May require small application-plane adjustments such as CORS, local proxy configuration, or API metadata improvements to support browser-based local development cleanly.
- Creates a new user-facing delivery surface that will influence future backend API design, especially around retail EDI workflows, status modelling, and operational summaries.
