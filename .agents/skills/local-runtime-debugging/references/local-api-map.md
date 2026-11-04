# Local API map

Use these stable localhost surfaces. The browser talks to same-origin Next.js BFF routes; those routes obtain tenant-aware bearer tokens server-side and call the unified Spring API.

| Surface | Base URL | Useful checks |
|---|---|---|
| Web and Better Auth | `http://localhost:3000` | `/api/auth/ok`, `/api/auth/jwks` |
| Documents BFF | `http://localhost:3000/api/documents` | document-set summaries, details, and representation metadata |
| Integration BFF | `http://localhost:3000/api/integration` | exchanges, mappings, partners, agreements, SSCC |
| MFT BFF | `http://localhost:3000/api/mft` | transfers, partners, certificates, local simulator |
| Unified Spring API | `http://localhost:8080` | `/actuator/health`, `/api-docs`, `/swagger-ui.html` |
| AS2 simulator | `http://localhost:8082` | `/actuator/health` |

## Authentication

Use `scripts/local-api.mjs` instead of copying local passwords or bearer tokens into shell commands. It calls `POST /api/local-test-login`, retains returned cookies in memory, and then calls the requested BFF route.

- `--persona admin` is the default and can inspect administrative MFT configuration.
- `--persona member` exercises ordinary workspace-member exchange access.
- Local bootstrap login exists only in development when `LOCAL_TEST_USER_BOOTSTRAP=true`.
- The internal tenant remains the stable `LOCAL_SEED_TENANT_ID`; do not infer tenant identity from an organization name.

## Error evidence

Integration and MFT error responses can include a safe error code and message, processing stage, retryability, recommended action, correlation ID, and bounded structured details. Preserve those fields in fixes and UI propagation.

Send `X-Correlation-ID` on direct backend requests. Do not log or display authorization headers, cookies, tokens, request payloads, EDI content, private certificates, or `.local/settings.json`.

## Direct backend calls

Prefer BFF routes. Call port `8080` directly only when isolating BFF/client behavior from backend behavior. Obtain authentication through the application flow; never read secrets from `.local` merely to bypass it. Health and API documentation endpoints are intentionally unauthenticated.
