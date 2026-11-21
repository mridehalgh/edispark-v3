## Overview

Expose stateless Streamable HTTP with the Spring AI 2.1 WebMVC MCP starter on Spring Boot 4.2. EdiSpark registers its tool feature with the existing Documents-compatible JSON Schema validator. Domain application services provide persisted evidence and run existing deterministic validation.

## Architecture

```mermaid
flowchart LR
    Client -->|OAuth with PKCE| Auth
    Client -->|Bound access token| MCP
    MCP --> Exchange
    MCP --> Mapping
    MCP --> Partnerships
    MCP --> MFT
```

## Components and Interfaces

Tool declarations describe the five inputs and structured results. Spring Security validates the MCP audience. The control plane verifies active membership, tenant opt-in, and client consent.

## Data Models

Version pins and IDs come from persisted domain records. Search continuation uses bounded tenant partition reads with a tenant-bound cursor.

## Correctness Properties

1. Tenant separation applies to every supplied identifier and continuation.
2. Tool calls preserve business state and expose only explicit evidence fields.

## Error Handling

Return stable codes for access denial, invalid input, stale evidence, and dependency failure. Keep payloads out of error messages and audit logs.

## Testing Strategy

Cover protocol discovery and calls, token isolation, read and preview behavior, and local fixture journeys. Exercise cursor binding across generated tenants and malformed continuations using jqwik for property coverage.
