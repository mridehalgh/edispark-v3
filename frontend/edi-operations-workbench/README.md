# EDI Operations Workbench

Operator-facing React frontend for the local EdiSpark documents APIs.

## Local Startup

1. Install dependencies:

```bash
npm install
```

2. Start the backend locally so the frontend can load the live OpenAPI contract from `http://localhost:8080/api-docs`.

3. Start the frontend:

```bash
npm run dev
```

4. Open the Vite URL printed in the terminal.

## Backend Expectations

- Default backend base URL: `http://localhost:8080`
- Default OpenAPI contract URL: `http://localhost:8080/api-docs`
- If the contract cannot be loaded, the shell stays in a recoverable failure state and blocks live workflow submissions.

## Override The Backend URL

Use `VITE_API_BASE_URL` when the backend is not running on the default port.

```bash
VITE_API_BASE_URL=http://localhost:18080 npm run dev
```

The frontend derives both the API request base URL and the OpenAPI discovery URL from that single override.

## OpenAPI Auto-Wiring

- On startup, `IntegrationProvider` calls `loadOpenApiContract()`.
- `loadOpenApiContract()` resolves the backend URL from `VITE_API_BASE_URL` or falls back to `http://localhost:8080`.
- The frontend fetches `/api-docs`, validates the returned document shape, and projects the live operation catalogue for the shell.
- Once connected, request workflows create a typed client with the resolved backend base URL and return live backend payloads to each page.
- When the backend contract changes, refresh the generated local types with:

```bash
npm run api:generate
```

## Verification

```bash
npm test
npm run lint
npm run build
```
