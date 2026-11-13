# Continuous integration

The repository currently has one GitHub Actions CI workflow; it does not deploy to production.
Pushes to main, pull requests and manual runs use the same checks.

Four independent jobs start immediately:

| Job | Work |
| --- | --- |
| Backend | Full Maven reactor verification, including architecture tests; two reactor threads and one test JVM per module |
| Frontend checks | Tooling tests, frontend tests, UBL view contracts and TypeScript |
| Frontend build | Production Next.js build with its compiler cache |
| API contracts | One composed-application export for all three APIs; schema validation, parallel SDK generation/type-checks, drift detection and PR breaking-change comparisons |

The final job retains the original required-check name **build**. It runs even after a dependency fails
and passes only when every dependency succeeded. Frontend matrix failures do not cancel the other matrix job.
New commits cancel obsolete runs for the same PR or branch.

## Dependency and cache boundaries

- Java jobs cache Maven dependencies and the wrapper. Node jobs install the pnpm version pinned in
  package.json before setup-node resolves the pnpm store cache, then use a frozen lockfile.
- Independent runners intentionally compile their own Java dependencies: contract verification does not wait
  for the backend test suite or share mutable Maven target directories. API export skips tests because
  the backend job runs the complete suite.
- Maven parallelism is bounded instead of multiplying reactor threads by CPU-scaled Surefire forks.
  SDK generation runs three clients concurrently, then their type-checks run concurrently.
- Only the Next.js compiler cache is restored. Auth databases and runtime environments are not cached/uploaded.
  The production build generates a disposable auth secret and uses a temporary database.
- Checkouts are shallow. PR comparisons fetch the exact base commit instead of the entire history or a moving branch.

## Failure evidence

Backend test reports and exported contracts are uploaded with seven-day retention, including on failure.
The SDK gate detects modified, staged and newly generated files. Breaking changes are checked for all
three APIs; only a contract absent from an otherwise valid baseline commit is treated as a new API.

## Local verification

Run pnpm test:tooling, pnpm contracts:check, and the web test/typecheck/build scripts.
Backend CI uses:

```sh
./mvnw -B -T 2 --no-transfer-progress verify -Dsurefire.forkCount=1
```

Run API export after backend verification locally, because both use the same target directories:

```sh
./mvnw -B -T 2 --no-transfer-progress -pl apps/edispark-api -am -Popenapi-export verify -DskipTests
pnpm contracts:check
```

CI configuration and SDK scripts have no demo-data impact. The MFT regression found by the complete suite
freezes lazy signed MIME output before repeated serialization/encryption; its round-trip test retains exact
signed-byte, payload, signature and MIC assertions. No fixture or stored payload is rewritten.

Actual hosted-runner timing must be measured after pushing. Local GitHub CLI credentials returned HTTP 401,
so remote failures and measured speedups are not claimed.
