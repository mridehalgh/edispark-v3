# EdiSpark Ops Console

Local operator frontend scaffold for the EDI operations console.

## Runtime configuration

- Default backend origin: `http://localhost:8080`
- Environment override: `VITE_BACKEND_ORIGIN`

Copy `.env.example` to `.env.local` to override the backend origin for local work.

## Local development

1. Start the Spring Boot backend from the repository root:
   ```bash
   ./mvnw spring-boot:run -pl application
   ```
2. Install frontend dependencies:
   ```bash
   npm install
   ```
3. Start the frontend dev server:
   ```bash
   npm run dev
   ```

The Vite dev server runs on `http://localhost:5173` and proxies `/api`, `/api-docs`, `/swagger-ui`, and `/swagger-ui.html` to the configured backend origin.
