## Why

The repository already exposes a documented local API, but there is no operator-facing frontend for exploring documents, schemas, and EDI workflows. A first frontend is needed now so product and engineering can validate the B2B EDI user experience early, using the existing local endpoints as the integration backbone instead of waiting for the full retail EDI platform to be modelled in the backend.

## What Changes

- Add the first operator-facing frontend using `shadcn/ui` for a modern B2B EDI control surface.
- Introduce a UK retail-oriented UX direction focused on inbound and outbound message operations, document status visibility, and exception-led workflows.
- Add automatic local API discovery so the frontend can wire itself to the running backend by reading local OpenAPI metadata instead of hardcoding endpoint contracts.
- Provide views for the currently available backend resources, especially document sets, documents, document versions, schema definitions, schema versions, content retrieval, derivatives, and validation actions.
- Add EDI-specific presentation patterns over the generic documents API, including message-type framing for ORDERS, ORDRSP, DESADV, RECADV, INVOIC, REMADV, PAYORD, PRICAT, INVRPT, and SLSRPT.
- Define a frontend foundation that can later absorb trading partner management, acknowledgements, routing, and workflow orchestration without a redesign.

## Capabilities

### New Capabilities
- `edi-operations-frontend`: A shadcn-based frontend for B2B EDI operators to monitor, inspect, create, and validate EDI-related resources with a retail-focused operational UX.
- `local-api-discovery`: Automatic local wiring from the frontend to the running backend through OpenAPI discovery, generated endpoint metadata, and environment-aware local configuration.

### Modified Capabilities
- None.

## Impact

- Adds a new frontend application and supporting build/runtime configuration.
- Uses the existing Spring Boot OpenAPI endpoint at `/api-docs` and Swagger metadata at `/swagger-ui.html` as the backend contract source.
- Requires frontend dependencies for React, routing, data fetching, and `shadcn/ui`.
- May require local development adjustments such as CORS, proxying, or a shared startup workflow so the frontend can connect to `http://localhost:8080` by default.
