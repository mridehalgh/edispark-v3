# MFT security and operations

This document describes the current MFT/AS2 implementation, the production AWS target, tenant isolation, and every category of sensitive data that remains in the system.

## Executive decision

Private AS2 keys must never be stored in the EdiSpark database, payload store, container filesystem, application configuration, or application logs in production. Production uses AWS KMS only and stores key references plus public X.509 certificate chains.

The cost-conscious hierarchy is:

- one symmetric KMS root and DynamoDB hierarchical key store per bounded cell, with one branch key per standard tenant;
- a dedicated symmetric KMS key only for tenants explicitly assigned that policy;
- one RSA `SIGN_VERIFY` key per deployment cell, shared by the cell's standard-tier tenants;
- one RSA `ENCRYPT_DECRYPT` key per deployment cell, shared by the same tenants.

The Hierarchical keyring reduces monthly key count and KMS request volume by protecting persisted tenant branch keys with the cell root and caching branch materials. It creates a unique data key for each encryption operation. This is the default tier; it deliberately accepts a cell-level root/key-store blast radius. Dedicated KMS mode remains available by explicit policy when a tenant needs independent physical-key disablement or deletion.

KMS RSA decryption supports RSA-OAEP, not RSA PKCS#1 v1.5. A partner requiring PKCS#1 v1.5 or one certificate/key for both signing and decryption cannot be activated on the production service. There is no alternate provider or local-key fallback.

The detailed key hierarchy and operational decision are in [`docs/mft/`](mft/README.md).

## Components and trust boundaries

```text
Browser -> Next.js BFF -> MFT control-plane API -> tenant metadata
                                  |
Trading partner <-> AS2 data plane + managed crypto -> immutable payload/evidence store
```

- The browser calls same-origin BFF routes. Access tokens remain server-side.
- The control plane owns partner profiles, certificate metadata, routes, transfer search, retry requests, and operational evidence.
- The authenticated connection-details API assembles a retailer onboarding pack from the current tenant's partner profile, deployment-advertised URLs and algorithms, and public X.509 certificate. It never returns PKCS#12 bytes, passwords, private keys, key references, or cross-tenant records.
- The public AS2 data plane receives partner traffic and resolves the workspace from the exact `(AS2-To, AS2-From)` pair before processing content.
- The crypto boundary owns signing and decryption. In production it is an AWS managed-key adapter, not the local secret store.
- Payloads and MDN evidence are immutable business records. They are referenced from metadata rather than stored in DynamoDB values.

## Exchange flows

### Outbound

1. An authenticated user or API client submits a file, partner ID, and idempotency key in a tenant context.
2. The service validates that the partner and certificate references belong to the same tenant.
3. The original bytes are written once to a tenant-scoped payload location and hashed with SHA-256.
4. A worker acquires a conditional lease for the transfer.
5. The AS2 adapter signs with the local signing key, encrypts to the partner's public encryption certificate, and sends the message.
6. The signed MDN, MIC, HTTP result, attempts, and timestamps are retained as evidence. A manual resend creates a new transfer linked to the original.

### Inbound

1. The AS2 endpoint performs an exact, case-sensitive lookup of `(AS2-To, AS2-From)` in the global inbound identity registry.
2. The configured partner public signing certificate is used to verify the message. The managed local decryption key decrypts it.
3. The service binds the request to the resolved tenant before writing any metadata or payload.
4. `(tenant ID, AS2 Message-ID, payload SHA-256)` prevents duplicate storage.
5. The payload and evidence are stored under the tenant boundary and a signed MDN is returned or scheduled.

Do not infer a tenant from a filename, URL parameter, unverified certificate subject, or caller-supplied tenant header.

## Tenant isolation

Control-plane requests require a valid JWT. Spring Security validates issuer, audience, algorithm, and signature; `MftTenantContextFilter` accepts the validated `tenant_id` claim and establishes the tenant context. Resource lookups include that tenant ID, so another workspace receives “not found” rather than another tenant's record.

Public AS2 traffic cannot carry the user's JWT. Its tenant boundary is the configured AS2 identity pair. A pair may belong to only one workspace. Local creation rejects an already assigned pair; production persistence must enforce this with an atomic uniqueness condition so two concurrent requests cannot claim it.

Production storage rules:

- DynamoDB partition and index keys begin with the tenant identity for tenant-owned records.
- The inbound AS2 identity registry is global, contains only the routing reference needed to find a tenant, and is conditionally unique.
- S3 object keys use non-guessable IDs beneath tenant prefixes. IAM access points or cell roles restrict access to the relevant tenant/cell.
- Symmetric tenant-data encryption resolves the expected branch from a trusted tenant registry and authenticates tenant ID, service, resource type, and resource ID. Asymmetric AS2 operations cannot use encryption context and are contained by cell/role authorization instead.
- Background jobs carry an explicit trusted tenant context obtained from persisted work, never from ambient request state.
- Logs and metrics include tenant/resource identifiers for authorization and audit, but never payload bytes, tokens, passwords, private keys, or certificate-upload contents.

The current local adapter keeps metadata in memory and encrypted files below `.local/mft`; it demonstrates logical isolation but is not production persistence.

## Sensitive-data inventory

“No private keys in EdiSpark” does not mean “no sensitive data.” EDI content and operational evidence can be commercially sensitive and can contain personal data.

| Data | Sensitive? | Current local behavior | Production target |
|---|---|---|---|
| Local hierarchical root key | Critical test secret | Random owner-readable file below `.local/mft`; derives branch/resource keys and is never committed | Does not exist; AWS KMS owns the cell root and protects branch keys |
| Hierarchical branch-key material | Critical secret | Derived only inside the local process | Decrypted and cached in cell workload memory for a bounded TTL; never logged, persisted plaintext, serialized, or included in heap/crash dumps |
| Local AS2 private keys and PKCS#12 password | Critical secret | AES-GCM encrypted with tenant/resource context below `.local/mft`; local profile only | Non-exportable cell-shared KMS signing/decryption keys; never accepted by the production control-plane API |
| Partner public X.509 certificates | Public, integrity-critical | Stored with tenant metadata/material | Store certificate/chain, SHA-256 fingerprint, purpose, validity, status, and rollover dates |
| EdiSpark public X.509 certificates | Public, integrity-critical | Stored with the local test identity | Store and distribute to partners; retain versions through rotation grace periods |
| EDI payloads | Confidential; may contain PII | AES-GCM encrypted with tenant/resource context below `.local/mft` | AWS Encryption SDK ciphertext in S3 with Block Public Access, TLS, SSE-S3 defence in depth, tenant-aware authorization, version/retention policy, and audited deletion |
| Filenames and content types | Potentially confidential | Transfer metadata | Minimise, tenant-scope, encrypt at rest, and avoid placing business data in names |
| AS2 IDs, endpoints, partner names, routing configuration | Confidential configuration | In-memory metadata | Tenant-scoped DynamoDB records encrypted at rest |
| MDNs, MICs, retry/error history, certificate fingerprints | Confidential operational evidence | Transfer metadata | Immutable tenant-scoped evidence with retention and access auditing |
| Access tokens, sessions, auth database | Secret/security-sensitive | Server-side Better Auth and local SQLite | Managed database/secret controls; tokens never persisted by the MFT API or sent to the browser-facing AS2 path |
| Root/dedicated KMS ARNs, branch IDs/versions, logical key-store name, certificate versions | Not secrets, authorization-sensitive | Deterministic local branch IDs are resolved by the test double | Store in a trusted registry; IAM, key-store permissions, context checks, cell assignment, and audit integrity provide protection |
| Application logs | Potentially sensitive | IDs and state only by design | Central log retention, masking, access control, and alerts; prohibit payload and secret logging |

Local test certificates and credentials under `.local` are disposable, ignored by Git, and must never be promoted to another environment. `pnpm local:reset` deletes this generated state.

## Certificate and key lifecycle

- Remote partners provide X.509 public certificates, not only bare public keys. Certificates provide purpose, identity binding, validity dates, issuer/chain information, fingerprints, and a workable rollover unit.
- EdiSpark provides partners with its public signing and encryption certificates. Private keys are never shared.
- Upload validates type, algorithm, key size, validity, chain, fingerprint, and allowed use before activation.
- Rotation supports overlapping active certificates. Inbound verification/decryption may accept old and new material during a bounded grace period; outbound uses exactly one active version.
- Expiry alerts are raised before partner-specific lead times. Revoked or expired material cannot be selected for new outbound traffic.
- Removal is a two-stage deactivate-and-retain process while evidence retention requires the public certificate. Private managed keys are scheduled for deletion only after the decrypt/replay retention window and explicit approval.

## AWS authorization and audit

The hierarchical runtime receives `dynamodb:GetItem` on its cell key store and `kms:Decrypt` on its root; it cannot create or rotate branches. It temporarily holds cached plaintext branch material in memory, so process inspection, heap dumps, crash dumps, debug endpoints, and broad node access are prohibited. A separate provisioning role must create/version branch keys and write the trusted tenant mapping. A signing worker receives `kms:Sign` for its cell signing key; an inbound worker receives `kms:Decrypt` for its cell decryption key. Dedicated-key roles are restricted to the selected tenant key and required context.

Provisioning roles are separate from transfer-processing roles. Certificate rotation and key scheduling are privileged, audited operations with approval. Production services use VPC endpoints where supported and must not require long-lived AWS access keys.

## Availability and recovery

- KMS, hierarchical key-store, or cache reauthorization failure stops affected new crypto work; ciphertext, queued work, and evidence remain durable.
- Retries are bounded, jittered, idempotent, and preserve the AS2 Message-ID where protocol replay requires it.
- Horizontal workers use owner-conditional DynamoDB leases with expiry and owner-safe release.
- Payload/evidence storage and metadata have independent backup and retention plans linked by immutable transfer IDs.
- Recovery tests prove that a tenant can restore metadata, payloads, public certificate history, and key references without exporting private keys.

## Current implementation status

The local vertical slice uses `phase2`, in-memory metadata, and a local KMS test double for encrypted payload/PKCS#12 storage. Production uses DynamoDB, S3 ciphertext, the Hierarchical keyring, owner-conditional worker leases, separate KMS signing/decryption keys, and production-only AS2/MDN endpoints. Production startup validates both public certificates against their KMS keys and fails closed.

Production activation still requires certificate rotation/revocation automation, CloudTrail alert wiring, restore exercises, per-tenant admission controls, and real-partner interoperability tests covering both MDN modes, rollover, malformed messages, maximum sizes, and tenant-boundary attempts. Terraform deployment resources are defined but have not been applied.
