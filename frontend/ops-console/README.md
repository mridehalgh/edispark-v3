# EdiSpark Ops Console

Local operator frontend for the retail and e-commerce EDI operations workbench.

## Runtime configuration

- Default backend origin: `http://localhost:8080`
- Environment override: `VITE_BACKEND_ORIGIN`

Copy `.env.example` to `.env.local` when you need to point the console at a different local backend:

```bash
cp .env.example .env.local
```

## Startup steps

1. Start the Spring Boot backend from the repository root:
   ```bash
   ./mvnw -pl application -am install -DskipTests
   java -jar application/target/application-1.0.0-SNAPSHOT.jar
   ```
2. Confirm the backend contract is available:
   ```bash
   curl http://localhost:8080/api-docs
   ```
3. Install frontend dependencies from `frontend/ops-console`:
   ```bash
   npm install
   ```
4. Start the frontend dev server:
   ```bash
   npm run dev
   ```
5. Open `http://localhost:5173`.

The Vite dev server proxies `/api`, `/api-docs`, `/swagger-ui`, and `/swagger-ui.html` to the configured backend origin.

## Local integration checklist

Use this quick pass after both processes are running:

1. The banner shows `Connection state: healthy`.
2. `Active backend` points to `http://localhost:8080` or your configured override.
3. `Contract source` resolves to `/api-docs`.
4. The API explorer lists discovered document-set, schema, derivative, and validation operations.
5. The document-set and schema screens stay usable, and degraded guidance appears only if the backend contract is unavailable.

## Test suite

Run the frontend tests from `frontend/ops-console`:

```bash
npm run test:run
```
