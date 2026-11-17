# Continuous integration

The repository has two workflows: **CI & Delivery** for verification and optional application delivery, and **Operations** for manual infrastructure and dependency operations. AWS jobs skip unless the repository Actions variable `CD_ENABLED` is exactly `true`.
Pushes to main, pull requests and manual runs use the same checks.

Backend, frontend, security and Terraform verification start in parallel. Contract and backend dependency scans consume artifacts from the backend build:

| Job | Work |
| --- | --- |
| Backend | Full Maven reactor verification, including architecture tests; two reactor threads and one test JVM per module |
| Frontend checks | Tooling tests, frontend tests, UBL view contracts and TypeScript |
| Frontend build | Production Next.js build with its compiler cache |
| API contracts | Reuse the backend export for all three APIs; schema validation, parallel SDK generation/type-checks, drift detection and PR breaking-change comparisons |
| Security | Secret history, source analysis and workflow checks; dependency scanning checks the built backend SBOM and JavaScript lockfile |
| Terraform | Offline initialization/validation and mock tests for six roots; no AWS credentials |

The final job retains the original required-check name **build**. It runs even after a dependency fails
and passes only when every dependency succeeded. Frontend matrix failures do not cancel the other matrix job.
New commits cancel obsolete checks for the same PR. Main delivery runs are not cancelled.

## Dependency and cache boundaries

- Java jobs cache Maven dependencies and the wrapper. Node jobs install the pnpm version pinned in
  package.json before setup-node resolves the pnpm store cache, then use a frozen lockfile.
- Backend verification exports contracts once. Contract verification downloads those exports; it does not compile Java again. Syft inventories the packaged Spring JAR, and dependency scanning consumes that CycloneDX SBOM instead of re-resolving raw Maven POMs against rate-limited public registries. JavaScript lockfiles and Terraform configuration remain scanned directly. OSV checks the backend SBOM and pnpm lockfile explicitly.
- Maven parallelism is bounded instead of multiplying reactor threads by CPU-scaled Surefire forks.
  SDK generation runs three clients concurrently, then their type-checks run concurrently.
- Only the Next.js compiler cache is restored. Auth databases and runtime environments are not cached/uploaded.
  The production build generates a disposable auth secret and uses a temporary database.
- Most checkouts are shallow. Secret scanning fetches full history; deployment fetches history for its ancestry guard.
- Dependency installation skips lifecycle scripts. SQLite is explicitly rebuilt from source in the web workspace, then opened in memory to verify the native binding.
- Terraform lockfiles include Linux AMD64/ARM64 and local macOS hashes; CI keeps `-lockfile=readonly`.
- Workflow security blocks medium/high findings. Low-severity syntax advice is advisory because the pinned actionlint does not support the suggested self-repository syntax. Secret scanning ignores only two exact historical findings for truncated documentation pagination tokens.

## Failure evidence

Backend test reports and exported contracts are uploaded with seven-day retention, including on failure.
The SDK gate detects modified, staged and newly generated files. Breaking changes are checked for all
three APIs; only a contract absent from an otherwise valid baseline commit is treated as a new API.

## Local verification

Run pnpm test:tooling, pnpm contracts:check, and the web test/typecheck/build scripts.
Backend CI uses:

```sh
./mvnw -B -T 2 --no-transfer-progress -Popenapi-export verify -Dsurefire.forkCount=1
```

The command above also exports all API contracts. Check their generated clients afterward:

```sh
pnpm contracts:check
```

CI configuration and SDK scripts have no demo-data impact. The MFT regression found by the complete suite
freezes lazy signed MIME output before repeated serialization/encryption; its round-trip test retains exact
signed-byte, payload, signature and MIC assertions. No fixture or stored payload is rewritten.

Hosted-runner results must be checked on the pushed commit; local checks alone do not establish that GitHub CI is green.
