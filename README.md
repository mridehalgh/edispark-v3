# EdiSpark

EdiSpark is a composable, multi-tenant EDI platform for receiving, validating, transforming, and routing business documents. This repository contains Documents and MFT bounded contexts, UBL and TRADACOMS standards support, Spring Boot APIs, a local AS2 peer, and a Next.js control plane.

## Architecture 🏗️

The repository is organized by responsibility rather than framework:

| Path | Purpose |
|---|---|
| `apps/documents-api` | Spring Boot composition root, HTTP security, OpenAPI, and runtime configuration |
| `apps/mft-api` | AS2/MFT Spring Boot composition root and public protocol endpoint |
| `apps/web` | Next.js UI, Better Auth server, and API-facing web routes |
| `domains/documents` | Documents domain, application services, REST ownership, and DynamoDB persistence |
| `domains/mft` | Partner, certificate, transfer, MDN evidence, retry, and AS2 adapters |
| `platform/crn` | Reusable canonical resource names |
| `platform/pagination` | Shared pagination contracts |
| `platform/tenancy` | Tenant context and isolation support |
| `standards/tradacoms` | TRADACOMS parsing and validation |
| `standards/ubl` | Generated and handwritten UBL Java support |
| `vendor/ubl/2.1` | Versioned upstream UBL schemas and examples |
| `contracts/documents` | Exported Documents OpenAPI contract |
| `contracts/mft` | Exported MFT OpenAPI contract |
| `packages/documents-client` | Generated, type-safe TypeScript client |
| `packages/mft-client` | Generated, type-safe MFT TypeScript client |
| `tooling/as2-simulator` | Local-only AS2 trading-partner simulator |
| `tooling/local-runtime` | Embedded DynamoDB support used only by local development |
| `tooling/scripts` | Cross-platform local orchestration scripts |
| `docs/specs` | Migrated feature requirements, designs, and task records |
| `openspec` | Active proposal workflow |

The intended shape is composable services: each domain owns its API and persistence ports, while application modules compose independently deployable processes. Business rules stay outside controllers and UI code.

## Prerequisites

- Java 21 or newer; CI compiles against Java 21
- Node.js 22
- Corepack with pnpm 10.15.0
- No Docker is required for local development

## Run everything locally 🚀

From the repository root:

```bash
corepack enable
pnpm install --frozen-lockfile
pnpm dev
```

The first start compiles and installs the local Maven modules, so it can take around a minute. Later starts reuse compiled output. The runner waits for both Java API health checks before launching Next.js, then warms the local JWKS route before printing `EdiSpark local stack is ready.` This prevents the first page load from racing the Java builds or Next.js route compilation. Stop the complete stack with `Ctrl+C`.

Before launching, the development runner terminates any processes listening on its configured local ports (3000, 8000, 8080, and 8081 by default) and verifies that each port is free. This prevents Next.js from silently selecting another port and keeps service URLs deterministic. The service-specific commands perform equivalent cleanup for their ports. Save or stop unrelated work using those ports before running these commands because its process will be terminated.

`pnpm dev` starts:

| Service | URL |
|---|---|
| Web application | http://localhost:3000 |
| Better Auth health | http://localhost:3000/api/auth/ok |
| Local JWKS | http://localhost:3000/api/auth/jwks |
| Documents API | http://localhost:8080 |
| API health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |
| MFT control plane | http://localhost:3000/mft |
| MFT API | http://localhost:8081 |
| MFT health | http://localhost:8081/actuator/health |
| MFT Swagger UI | http://localhost:8081/swagger-ui.html |
| MFT OpenAPI JSON | http://localhost:8081/api-docs |
| Embedded DynamoDB | http://localhost:8000 |

The local runner creates `.local/settings.json` with a random Better Auth secret, migrates `.local/auth.db`, starts persistent embedded DynamoDB, creates the Documents table, and loads safe sample documents. These generated files are ignored by Git.

### Local authentication

The local runner provisions this development-only account on the first authentication request:

| Field | Value |
|---|---|
| Username / email | `local@edispark.test` |
| Password | `EdiSpark-Local-2026!` |

When running `pnpm dev`, the sign-in page shows a floating **Local test login** button in the lower-right corner. Select it to sign in with the test account and open the automatically provisioned `EdiSpark Local` workspace in one step. You can also enter the credentials above manually.

The button and its login route are development-only: the control is absent from the production client bundle and the route returns HTTP 404 outside `next dev`. The predictable credential is enabled only by `tooling/scripts/local-environment.mjs`; do not enable `LOCAL_TEST_USER_BOOTSTRAP` in a deployed environment.

You can still create additional accounts in the web application. When Resend is not configured, verification and recovery emails are printed to the web process output instead of being sent.

The first local workspace is mapped to the same tenant as the seeded Documents data. Additional workspaces receive distinct tenant IDs. The web application issues ES256 JWTs, publishes the matching public key through its JWKS endpoint, and the Spring API validates issuer, audience, signature, and the `tenant_id` claim. API routes remain protected in the local profile; there is no authentication bypass.

## Common commands 🧰

| Command | Purpose |
|---|---|
| `pnpm dev` | Run the complete local stack |
| `pnpm dev:web` | Run only Next.js with local auth settings |
| `pnpm dev:api` | Build dependencies and run only the Spring API with embedded DynamoDB |
| `pnpm dev:mft-api` | Build dependencies and run only the local MFT API |
| `pnpm dev:as2-simulator` | Run the optional local AS2 trading-partner peer |
| `pnpm mft:lab:certs` | Generate disposable local AS2 lab certificates |
| `pnpm local:reset` | Delete generated `.local/` auth, settings, and DynamoDB state |
| `./mvnw -B clean test` | Clean and test every Java module |
| `pnpm --filter @edispark/web test` | Run web unit and UBL contract tests |
| `pnpm --filter @edispark/web typecheck` | Type-check the web application |
| `pnpm --filter @edispark/web build` | Create a production Next.js build |
| `pnpm contracts:sync:documents` | Export OpenAPI and regenerate the TypeScript client |
| `pnpm contracts:sync:mft` | Export MFT OpenAPI and regenerate the TypeScript client |
| `pnpm contracts:check` | Verify the generated client is type-safe and current |
| `pnpm check` | Run the combined Java, web, build, and contract checks |

Some Java tests and contract export tasks start local servers. If an execution sandbox blocks loopback sockets, allow local port binding and rerun the same command.

## Configuration 🔐

The local runner supplies safe defaults; copying an environment file is not required. [`apps/web/.env.example`](apps/web/.env.example) documents the web and production-auth settings.

| Variable | Local default | Purpose |
|---|---|---|
| `BETTER_AUTH_URL` | `http://localhost:3000` | Auth issuer and browser origin |
| `BETTER_AUTH_SECRET` | Generated in `.local/settings.json` | Session and auth secret |
| `BETTER_AUTH_DATABASE_PATH` | `.local/auth.db` | Better Auth SQLite database |
| `BETTER_AUTH_AUTO_MIGRATE` | `true` from the local runner | Migrates auth tables locally only |
| `LOCAL_TEST_USER_BOOTSTRAP` | `true` from the local runner | Provisions the documented local test account |
| `LOCAL_TEST_USER_EMAIL` | `local@edispark.test` | Local test-account email |
| `LOCAL_TEST_USER_PASSWORD` | `EdiSpark-Local-2026!` | Local-only predictable test password |
| `DOCUMENTS_API_URL` | `http://localhost:8080` | Server-only API URL used by Next.js |
| `MFT_API_URL` | `http://localhost:8081` | Server-only MFT API URL used by Next.js |
| `MFT_ALLOW_PRIVATE_NETWORKS` | `true` locally | Allows the loopback simulator; keep `false` in production |
| `LOCAL_SEED_TENANT_ID` | Stable local KSUID | Links the first workspace to sample data |
| `DYNAMODB_LOCAL_ENDPOINT` | `http://localhost:8000` | Local DynamoDB endpoint |
| `DYNAMODB_LOCAL_DATA_DIR` | `.local/dynamodb` | Persistent local DynamoDB files |
| `AUTH_ISSUER` | `http://localhost:3000` | JWT issuer expected by Spring Security |
| `AUTH_JWKS_URI` | `http://localhost:3000/api/auth/jwks` | Public signing-key endpoint |
| `AUTH_AUDIENCE` | `http://localhost:3000` | JWT audience expected by the API |
| `AUTH_JWS_ALGORITHM` | `ES256` | JWT signature algorithm trusted by Spring Security |

Never commit secrets, private keys, real customer data, or local databases. Production must use explicit Better Auth migrations, HTTPS, managed secrets, a production database, remote JWT signing, and managed persistence. Local automatic migrations and embedded storage are development conveniences only.

The production MFT design does not allow private AS2 keys in the application database or filesystem. It uses a cost-conscious KMS hierarchy: one symmetric key per tenant across services plus separate cell-shared AS2 signing and decryption keys. KMS-native AS2 requires RSA-OAEP and separate certificates; incompatible partners cannot be activated. Start with the detailed [MFT documentation](docs/mft/README.md) and [sensitive-data inventory](docs/mft-security-and-operations.md).

## Contracts

The Documents API is the source of truth for `contracts/documents/openapi.json`. After changing a controller or DTO, run:

```bash
pnpm contracts:sync:documents
pnpm contracts:check
```

Commit the API change, exported contract, and generated client together. The web application consumes `@edispark/documents-client` through the pnpm workspace rather than maintaining handwritten request types.

## Troubleshooting 🔎

- **A local port cannot be released:** the runner first requests graceful shutdown and then forces the owning process to stop if necessary. If cleanup still fails, check whether the listener belongs to another user or requires elevated permission.
- **The API does not become ready:** `pnpm dev` waits up to two minutes for `http://localhost:8080/actuator/health` before starting Next.js. Check the Java output for the first startup failure.
- **The MFT control plane says the service is unavailable:** use `pnpm dev`, which now starts and checks the MFT API, or run `pnpm dev:mft-api` beside an already running web app. Verify `http://localhost:8081/actuator/health`.
- **Authentication or JWKS fails:** verify `http://localhost:3000/api/auth/ok` first, then reset generated state with `pnpm local:reset` and restart.
- **Local data is inconsistent:** stop the stack, run `pnpm local:reset`, and run `pnpm dev`. Reset removes all generated local users, workspaces, keys, and documents.
- **DynamoDB native-library errors:** run `./mvnw -B -pl tooling/local-runtime -am clean package -DskipTests`, then restart the API.
- **Generated client drift:** run `pnpm contracts:sync:documents` and include both contract and client changes.
- **Wrong Java release:** ensure `java -version` reports 21 or newer and that `JAVA_HOME` points to that installation.

## Engineering guidance

Repository rules live in `AGENTS.md` and `.agents/rules/`. Feature specifications under `docs/specs/` should be checked against the current implementation before changing their areas. New proposals use the `openspec/` workflow.
