# UK deployment and trunk-based delivery

The deployment implementation is provided as code, with account and DNS placeholders. No AWS resources or GitHub settings are applied by editing this repository. Deployment is currently opt-in: leave the repository Actions variable `CD_ENABLED` unset (or set it to `false`). The CI & Delivery workflow keeps all delivery stages visible but skips dependency fetching, packaging, signing, staging and production. The Operations workflow also skips infrastructure planning/applying and dependency admission, including on manual runs. Build, test, contract, security and offline Terraform checks remain active; the required `build` check depends only on those verification jobs.

Set `CD_ENABLED` to the exact string `true` only after configuring the AWS roles, environments and release inputs and reviewing the deployment architecture and budget. Before enabling automatic promotion, complete bootstrap and the deployed acceptance checks below using the reviewed initial provisioning procedure. Missing configuration after opting in is an error, rather than a successful deployment.

## Trunk and promotion

`main` is the sole integration and deployment branch. Open small, short-lived PRs against it, squash merge after independent review and CI, then delete the branch. There are no staging, production, develop or release branches. Keep unfinished features disabled behind runtime configuration until ready; do not accumulate long-running feature branches. Dependency bots open reviewed PRs and cannot merge themselves.

Every main commit starts quality checks independently. A release fetches admitted dependencies, builds once, scans and signs its artifacts, then promotes the same bytes through staging and production. Only promotion is serialized. GitHub can coalesce pending concurrency runs to the newest candidate; skipped commits remain included in its history and migration bundle. The ancestry check prevents an older queued build replacing newer trunk code. Cancel obsolete PR checks, never an active promotion or infrastructure apply.

Import `.github/rulesets/trunk.json` and apply `.github/repository-settings.example.json` during repository setup. Require the `build` check, linear history, resolved discussions, dismissal of stale approvals, a code-owner review and approval of the latest push by someone else. No routine bypass is configured. Replace the bootstrap CODEOWNERS identity with the actual reviewing team before launch. These files are templates, not evidence that remote settings were changed.

## Architecture and boundaries

| Capability | Placement and control |
| --- | --- |
| Frontend | Node 24 native Lambda Web Function; home/control plane Ireland, London `PerRegion` origin selected explicitly by CloudFront |
| API | One Java 21 Spring application, ARM64 Fargate, two London availability zones |
| Authentication | London Aurora DSQL, IAM/TLS, dedicated `auth` schema; no RDS Proxy or SQL password |
| Business persistence | Existing DynamoDB and tenant-encrypted MFT S3, London document S3, versioning/PITR and deletion protection |
| Ingress | CloudFront/WAF; private ALB VPC origin; IAM/OAC and explicit distribution policy for the frontend |
| Egress | Fargate public IPv4 addresses and internet gateway, HTTPS only; no NAT gateways or NAT instances |
| Infrastructure | Terraform only; CloudFormation stack operations denied to CI identities; native provider Cloud Control operations remain permitted |

The internal ALB has no public listener. Task security groups accept application requests only from the ALB; possessing a public task IP does not allow direct incoming connections. ALB-to-task HTTP runs inside these restricted VPC boundaries. Fargate source IPs change on replacement: partners requiring fixed source-IP allowlisting need a separate reviewed design before activation.

Web Function management, replicated configuration and centralized frontend logs reside in Ireland. CloudFront/WAF are global; edge certificates and WAF control resources use `us-east-1`. This is not a strict UK-only telemetry/control-plane arrangement. Do not log documents, cookies, tokens, email bodies or secret values in production. WAF logging redacts credentials/query strings and stores blocked requests with bounded retention.

The common WAF body-size rule counts rather than blocks legitimate larger documents. Application request limits and EDI validation enforce size/content; AS2 bypasses generic body inspection because it transports signed binary MIME, while keeping IP reputation and rate controls. Verify real representative upload/AS2 sizes and signatures before enforcing partner traffic.

## Bootstrap order

1. Use separate tooling, staging and production accounts. Examples in `infrastructure/terraform/environments/` deliberately use nonexistent IDs and `.invalid` domains. Terraform checks the expected account ID before making changes.
2. Apply `bootstrap` once per account using an administrator with MFA. It creates versioned encrypted state storage, locked release storage, OIDC identities, explicit CI-role policies and, in tooling, the release signing key and package repositories. Initially use an empty `role_bindings` map. Protect the initial local bootstrap state; after the bucket exists, add an S3 backend declaration to the bootstrap root and migrate its state into that bucket. Foundation/release/edge already declare S3 backends. Never use local state for shared ongoing operations.
3. Configure each S3 backend with the bootstrap `state_backend` output plus a unique key, such as `staging/foundation.tfstate`, `staging/release.tfstate`, or `staging/edge.tfstate`. `use_lockfile=true`, KMS encryption and versioning are mandatory. Never disable locking or hand-edit state.
4. Apply the UK `foundation` using its environment example and actual zone/account values. Obtain `release_config`, `backend_certificate_arn`, `assets_bucket`, and `assets_key_arn`. The existing MFT/integration directories remain reusable modules; do not separately deploy a second application from their legacy optional service configuration.
5. Create the scoped DSQL roles/schema from `apps/web/migrations/bootstrap.sql.example`, one statement at a time, using a temporary administrator. Map the web execution IAM role to `edispark_auth` and the migration IAM role to `edispark_migrator`. Revoke temporary `DbConnectAdmin` afterwards. The runtime receives data permissions only; CI migrations receive schema ownership, not database administration.
6. Set the web Secrets Manager value out of band: `BETTER_AUTH_SECRET`, `RESEND_API_KEY`, and `EMAIL_FROM`. Never put values in Terraform inputs/state, GitHub artifacts, image layers or a `.env` file in a package. The Web Function entry point retrieves the exact London secret before loading Next.
7. Configure package admission, CI identities, repository protection and environments below. Publish an initial signed release, apply its initial `release` plan through infrastructure review, then apply `edge` with that root's `edge_config` output. Pass the asset bucket/key outputs to edge. The edge state exclusively manages the asset KMS policy and frontend invocation policy. Initial creation is reviewed; automatic promotion requires a healthy existing baseline.
8. Run deployed acceptance and recovery checks, set `DEPLOYMENT_READINESS=verified` for each deployment environment, then set repository `CD_ENABLED=true`. Normal application releases thereafter require no manual production approval. Sensitive infrastructure changes still require review.

## CI identity and environment configuration

Customize the GitHub OIDC subject with `include_claim_keys=["repo","context","job_workflow_ref","ref"]`. Each Terraform role binding requires the exact repository, protected environment, reusable workflow at `refs/heads/main`, caller `ref:refs/heads/main`, and audience `sts.amazonaws.com`. Do not replace these with wildcards. If the repository uses immutable subject claims containing owner/repository IDs, inspect only its `sub` and use the exact matching repository subject in the trust policy; never print the raw token. Configure environment deployment branches to allow only main.

Supply reviewed least-privilege policy JSON in `role_bindings`; no default administrative grant is provided. Keep these roles separate:

| Environment / reusable workflow | Role and minimum resource scope |
| --- | --- |
| `dependency-fetch` / `fetch-dependencies.yml` | CodeArtifact authorization on the tooling domain, read/get-endpoint on the two approved repositories, `sts:GetServiceBearerToken` constrained to CodeArtifact; no publishing or signing |
| `dependency-admission` or `dependency-emergency` / `admit-dependencies-apply.yml` | Describe exact ingress versions and copy to approved repositories; no overwrite/delete; independent environment approval required |
| `release-signing` / `sign-release.yml` | `kms:Sign` on the release RSA key only; never JWT/AS2 keys or runtime secrets |
| `staging-migrations`, `production-migrations` / `deploy.yml` | `dsql:DbConnect` on that environment's cluster, mapped only to the migration DB role; no `DbConnectAdmin` |
| `staging`, `production` / `deploy.yml` publisher | ECR authorization/upload on that environment's repository, put/read versioned release objects, put hashed assets, scoped S3 KMS encryption permissions; no signing/IAM changes |
| `staging`, `production` / `deploy.yml` deployment | State read/write for release state and its lock only; scoped ECS registration/service update, Web Function revision creation/routing, required Cloud Control create/update/read operations, resource descriptions and deployment alarm reads; PassRole only for the exact runtime/execution roles and service principals; no customer S3 reads, signing, IAM changes or secret reads |
| `{environment}-{root}-plan` / `infrastructure-apply.yml` | Scoped state access/locking and infrastructure descriptions; no customer object/secret values |
| `{environment}-{root}-auto` / `infrastructure-apply.yml` | Only the explicitly approved automatic-change capabilities |
| `{environment}-{root}-review` / `infrastructure-apply.yml` | Reviewed infrastructure capabilities, constrained to that account/cell; require independent approval before issuing credentials |

Foundation/runtime IAM policies are implemented in Terraform. CI policy documents remain account-specific bootstrap inputs so existing organizational boundaries can be attached without inventing account identifiers or granting broad defaults. Register only the operations/resource ARNs needed by the validated plans. CI identities explicitly deny CloudFormation stack management and public Web Function auth mode, even if another attached policy allows them.

Required GitHub variables:

- Repository: `CD_ENABLED` (leave unset initially).
- Fetch/admission: `AWS_TOOLING_ACCOUNT_ID`, `AWS_DEPENDENCY_FETCH_ROLE_ARN` or `AWS_DEPENDENCY_ADMISSION_ROLE_ARN`, `CODEARTIFACT_DOMAIN`, and fetch's `CODEARTIFACT_NPM_ENDPOINT` / `CODEARTIFACT_MAVEN_ENDPOINT`.
- Signing: `AWS_TOOLING_ACCOUNT_ID`, `AWS_RELEASE_SIGNER_ROLE_ARN`, `RELEASE_SIGNING_KEY_ARN`.
- Migration: `AWS_ACCOUNT_ID`, `AWS_MIGRATION_ROLE_ARN`, `BETTER_AUTH_DSQL_HOST`, `PUBLIC_RELEASE_KEY_PEM`.
- Deployment: `AWS_ACCOUNT_ID`, `AWS_DEPLOYMENT_ROLE_ARN`, `AWS_PUBLISHER_ROLE_ARN`, `PUBLIC_RELEASE_KEY_PEM`, `FRONTEND_URL`, `BACKEND_URL`, `ASSETS_BUCKET`, `DEPLOYMENT_READINESS`, `DEPLOYMENT_ALARMS_JSON`, `TF_BACKEND_CONFIGURATION_JSON`, `RELEASE_CONFIGURATION_JSON`.
- Infrastructure plan/apply environments: `AWS_ACCOUNT_ID`, the respective `AWS_INFRASTRUCTURE_PLAN_ROLE_ARN` / `AWS_INFRASTRUCTURE_APPLY_ROLE_ARN`, `TF_BACKEND_CONFIGURATION_JSON`; plan also needs `TF_VARIABLES_JSON`.

`RELEASE_CONFIGURATION_JSON` contains the foundation `release_config` as `foundation`, plus `domain_name`, `backend_certificate_arn`, public `as2_certificates` (`signing`/`encryption`), `alarm_actions`, and bootstrap `release_bucket`. It contains no secrets. The pipeline adds immutable image/object versions and retains previous revisions. `DEPLOYMENT_ALARMS_JSON` maps `eu-west-2` to the release API alarm name and `us-east-1` to the edge frontend alarm name. Missing alarms stop promotion.

## Dependencies and release evidence

The tooling account has separate npm/Maven ingress repositories and approved repositories. Approved repositories have no upstreams, preventing public fallback and internal namespace confusion. Populate ingress through the native package endpoint, review its exact package revision and provenance, then submit `.github/dependencies/admission.json` in a reviewed PR. Records use `{format,namespace,package,version,revision}`. The admission workflow requires seven days since the recorded publication timestamp and refuses overwrite. An emergency requires a tracked security issue and separate protected environment approval; it cannot bypass secret/malware/vulnerability gates. Vendor artifacts outside Maven Central, including DynamoDB Local, require explicit verified ingestion; never add a public fallback to make the release build pass.

Fetch credentials can only read the approved repositories. The isolated stores contain no settings/token files and expire as GitHub artifacts after one day. Builder jobs have no AWS/OIDC permissions, use offline package/Maven resolution, disable installation scripts, and compile only the explicitly allowed SQLite binding against the verified Node distribution headers. Privileged signing/deployment jobs use that admitted store without package scripts. ARM64 scanner binaries, Terraform, Java base images and Actions are pinned; scanner downloads verify committed checksums. Dependency update PRs require review.

Release evidence binds the source commit/repository/workflow, exact frontend ZIP, backend image archive, migration bundle, combined backend/frontend CycloneDX SBOM, and scan report. The KMS RSA-PSS signature is checked against the pinned public key before any migration/deployment permissions are obtained. Modified bytes, stale scans (>24 hours), wrong identity or missing evidence fail closed. Object versions and image digests are immutable; do not rebuild a release separately for production. Re-running all packaging jobs for an already published commit must not overwrite its release key; use a new reviewed trunk commit or reuse the original artifacts.

## Deployment, failure and recovery

Migrations execute only their signed bundle, one DDL per transaction, waiting for async indexes. A per-step checksum ledger rejects edited applied migrations. A crash between a DDL and its ledger record requires schema inspection/reconciliation; duplicate-object errors are not blindly ignored. Serialize migrations; use expand/contract changes compatible with the healthy rollback release. Runtime imports never apply DSQL migrations.

ECS retains old task definitions, uses 100% minimum healthy capacity, circuit breaker and alarm rollback. Frontend traffic advances 10%/10 minutes, 50%/10 minutes, then 100%/5 minutes. Synthetic probes and configured alarms run throughout. A failed promotion restores the baseline image/routing while retaining failed revisions for investigation. Rollback does not reverse migrations or resend partner documents. Runtime patching for native Web Functions is AWS-managed: application rollback cannot reverse an AWS runtime patch.

Infrastructure plans apply the reviewed saved bytes against locked state and the same source commit. Initial creation, deletion, permissions, auth settings, secret/key access and unknown changes require review. Automatic classification admits only narrowly matched code/revision/routing changes. Concurrent state changes invalidate a saved plan and require replanning; never force through a stale plan. Infrastructure release plans preserve live image/revision/traffic inputs rather than replaying stale configuration.

Before production enablement, record evidence for authentication/email/MFA/passkeys/SSO, DSQL concurrency and schema compatibility, two-tenant isolation, upload/AS2 signature/body behavior through WAF, IPv4 delivery to a controlled simulator, origin-bypass denial, rollback with retained static assets, signing/revocation response, and restoration of DSQL/DynamoDB/S3 data into an isolated account. Confirm account-wide audit trails, alert recipients, access restrictions, budgets and recovery objectives. Repository mock/unit tests do not substitute for these deployed checks.

The approved HTTPS-egress exception is scoped to the task rule `AVD-AWS-0104`, expires on 2027-02-01, and is owned by the platform/security reviewers. Its purpose is direct IPv4 partner delivery without NAT; ALB-only ingress and application SSRF controls remain enforced. No vulnerability or secret exception is supplied.

Production-only infrastructure/adapters have no new demo-data fixture requirement. Local SQLite identity and deterministic fixtures remain unchanged; the local auth verification script uses disposable data and never resets the workspace.
