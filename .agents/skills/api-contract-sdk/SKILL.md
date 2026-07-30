---
name: api-contract-sdk
description: Export an EdiSpark Spring API module as an OpenAPI file and generate its type-safe TypeScript frontend SDK. Use whenever adding or changing an API module, endpoint, DTO, frontend API integration, Next.js UI, contract validation, breaking-change detection, or local frontend development wiring that calls a backend API.
---

# API Contract SDK

Use the backend contract as the source of truth. Commit the generated OpenAPI document and generated TypeScript output; never hand-write duplicate endpoint types or call a backend with ad-hoc `fetch`.

## Non-negotiable frontend rule

For any Next.js/frontend work that calls an EdiSpark backend API:

1. Find the generated `@edispark/<module>-client` package.
2. Use its factory and generated SDK methods; do not construct URLs, request payload types, response types, or `Authorization` headers by hand.
3. Create the SDK in a server-only BFF boundary (route handler, Server Action, or server module), with a lazy OIDC token provider.
4. Client components call only same-origin BFF routes or receive data from Server Components. Never expose backend URLs or access tokens to browser JavaScript.
5. If no generated client exists, stop feature implementation long enough to add the contract/SDK workflow below. Do not create a one-off frontend client.

## Current conventions

- Export contracts to `api-contracts/<module>/openapi.json`.
- Generate SDKs into `packages/<module>-client/src/generated` with `@hey-api/openapi-ts`, Fetch transport, SDK methods, and Zod validators.
- Keep a small hand-written package-root factory for base URL, lazy token retrieval, correlation headers, and `throwOnError`.
- APIs use OIDC bearer tokens. Bind tenant context only from a validated `tenant_id` claim; never accept a tenant identifier from a frontend header or body.

## Required working order

Follow this order whenever frontend work needs backend data. Do not skip ahead to UI code.

1. **Inspect** the existing contract, generated package, BFF boundary, and the API module that owns the capability.
2. **Change the backend first**: controller/DTO/validation/tenant authorization/error handling, stable `operationId`, and OpenAPI metadata.
3. **Export the file** with `pnpm contracts:export:<module>`; review the generated OpenAPI diff before continuing.
4. **Generate and check the SDK** with `pnpm contracts:generate:<module>` and strict TypeScript checking. Resolve contract/type defects here, not in the UI.
5. **Wire the server BFF** using `create<Module>Client()` and the authenticated server session. Map typed SDK errors to safe UI responses.
6. **Implement the UI** against the BFF or Server Component data. Client Components must not import the generated backend client, use direct backend `fetch`, or handle bearer tokens.
7. **Prepare local development**: run `pnpm dev:prepare` before starting the frontend. When a Next.js app has a `dev` script, its `predev` must run the relevant `contracts:sync:<module>` command.
8. **Validate** the module tests, export, generation, typecheck, and diff. CI performs the same sequence plus a breaking-change comparison.

## Add or change a module

1. Inspect controllers, DTOs, exception handler, security chain, workspace manifests, and CI. Preserve unrelated changes.
2. Give every operation a stable, unique `operationId`. Specify correct success and error schemas; use the shared typed error model. Add bearer security metadata.
3. Enable Java parameter metadata (`<parameters>true</parameters>`) so exported path names are correct.
4. Add an `openapi-export` Maven profile. Start/stop the application with Spring Boot Maven and use `springdoc-openapi-maven-plugin` to write `../api-contracts/<module>/openapi.json` in `integration-test`.
   - Activate a dedicated `openapi-export` Spring profile with safe local dependencies.
   - Disable seeders and slow startup work under that profile.
   - Never use `curl` or a manually started process to obtain the contract.
5. Add `packages/<module>-client` with `openapi-ts.config.ts` reading the committed contract. Generate Fetch client, SDK methods, and Zod validators. Keep generated files under `src/generated`; only edit the factory and package exports.
6. Add root scripts `contracts:export:<module>`, `contracts:generate:<module>`, `contracts:sync:<module>`, and a `contracts:check` that regenerates, type-checks, and fails on Git differences.
7. Wire local development: add the relevant `contracts:sync:<module>` command to the real Next.js app's `predev`. If the UI does not yet exist, supply the sync command as the required local bootstrap rather than inventing an app shell.
8. Update CI to export, regenerate/type-check, and compare the PR contract to its base with `oasdiff breaking`; skip only when the base has no contract.

## Verify

Run module tests, the export profile, SDK generation, strict TypeScript checks, and `git diff --check`. Confirm the contract contains paths, operation IDs, bearer security, named path parameters, and typed errors.
