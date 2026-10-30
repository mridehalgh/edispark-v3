# MFT security and operations

This document describes the current MFT/AS2 implementation, the production AWS target, tenant isolation, and every category of sensitive data that remains in the system.

## Executive decision

Private AS2 keys must never be stored in the EdiSpark database, payload store, container filesystem, application configuration, or application logs in production. Production uses AWS KMS only and stores key references plus public X.509 certificate chains.

The cost-conscious hierarchy is:

- one symmetric customer-managed key per tenant, reused across approved EdiSpark services for payload/evidence encryption;
- one RSA `SIGN_VERIFY` key per deployment cell, shared by the cell's standard-tier tenants;
- one RSA `ENCRYPT_DECRYPT` key per deployment cell, shared by the same tenants.

The AWS Architecture Blog pattern is one symmetric key **per tenant across services**, not one key for every tenant. The cell-shared AS2 keys are a separate optimization. AWS KMS asymmetric calls do not support encryption context, so they provide cell-level—not per-tenant cryptographic—separation. Exact partnership authorization, separate worker roles, cell isolation, and audit controls are mandatory.

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
- Symmetric tenant-data encryption context includes tenant ID and resource type. Asymmetric AS2 operations cannot use encryption context and are contained by cell/role authorization instead.
- Background jobs carry an explicit trusted tenant context obtained from persisted work, never from ambient request state.
- Logs and metrics include tenant/resource identifiers for authorization and audit, but never payload bytes, tokens, passwords, private keys, or certificate-upload contents.

The current local adapter keeps metadata in memory and encrypted files below `.local/mft`; it demonstrates logical isolation but is not production persistence.

## Sensitive-data inventory

“No private keys in EdiSpark” does not mean “no sensitive data.” EDI content and operational evidence can be commercially sensitive and can contain personal data.

| Data | Sensitive? | Current local behavior | Production target |
|---|---|---|---|
| Local KMS root key | Critical test secret | Random owner-readable file below `.local/mft`; derives logical per-tenant AES keys and is never committed | Does not exist; AWS KMS owns non-exportable key material |
| Local AS2 private keys and PKCS#12 password | Critical secret | AES-GCM encrypted with tenant/resource context below `.local/mft`; local profile only | Non-exportable cell-shared KMS signing/decryption keys; never accepted by the production control-plane API |
| Partner public X.509 certificates | Public, integrity-critical | Stored with tenant metadata/material | Store certificate/chain, SHA-256 fingerprint, purpose, validity, status, and rollover dates |
| EdiSpark public X.509 certificates | Public, integrity-critical | Stored with the local test identity | Store and distribute to partners; retain versions through rotation grace periods |
| EDI payloads | Confidential; may contain PII | AES-GCM encrypted with tenant/resource context below `.local/mft` | S3 with Block Public Access, TLS, SSE-KMS, tenant-aware authorization, version/retention policy, and audited deletion |
| Filenames and content types | Potentially confidential | Transfer metadata | Minimise, tenant-scope, encrypt at rest, and avoid placing business data in names |
| AS2 IDs, endpoints, partner names, routing configuration | Confidential configuration | In-memory metadata | Tenant-scoped DynamoDB records encrypted at rest |
| MDNs, MICs, retry/error history, certificate fingerprints | Confidential operational evidence | Transfer metadata | Immutable tenant-scoped evidence with retention and access auditing |
| Access tokens, sessions, auth database | Secret/security-sensitive | Server-side Better Auth and local SQLite | Managed database/secret controls; tokens never persisted by the MFT API or sent to the browser-facing AS2 path |
| KMS key ARNs, aliases, certificate versions, AWS resource IDs | Not secrets, authorization-sensitive | Logical tenant aliases are resolved by the local test double | Store as metadata; IAM, key policy, cell assignment, and registry integrity provide protection |
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

The runtime role receives only the precise KMS actions for the selected operation. A signing worker receives `kms:Sign` for its cell signing key; an inbound worker receives `kms:Decrypt` for its cell decryption key. Neither receives key administration or deletion permissions. Tenant-data workloads assume sessions restricted to the tenant alias and required symmetric encryption context. KMS key policies, service/network conditions, CloudTrail, and alarms provide enforcement and audit evidence.

Provisioning roles are separate from transfer-processing roles. Certificate rotation and key scheduling are privileged, audited operations with approval. Production services use VPC endpoints where supported and must not require long-lived AWS access keys.

## Availability and recovery

- KMS or HSM unavailability stops new signing/decryption; ciphertext, queued work, and evidence remain durable.
- Retries are bounded, jittered, idempotent, and preserve the AS2 Message-ID where protocol replay requires it.
- Horizontal workers require conditional leases. An in-memory lock is not a production lease.
- Payload/evidence storage and metadata have independent backup and retention plans linked by immutable transfer IDs.
- Recovery tests prove that a tenant can restore metadata, payloads, public certificate history, and key references without exporting private keys.

## Current implementation status

The local vertical slice uses `phase2`, in-memory metadata, and a local KMS test double for AES-GCM encrypted payload/PKCS#12 storage. Its ciphertext is bound to tenant, service, resource type, and resource ID. Those beans are enabled only for the `local` and `openapi-export` Spring profiles. A production start without an AWS adapter fails closed because required MFT beans are absent.

Before production, implement and verify:

1. DynamoDB metadata, atomic inbound-identity ownership, and distributed worker leases.
2. S3 immutable payload/evidence storage with tenant-aware SSE-KMS policies and lifecycle rules.
3. The KMS signer/decrypter adapter, cell key registry, partner RSA-OAEP capability gate, and no private-key upload path.
4. Certificate rotation, revocation policy, expiry alarms, CloudTrail alerts, and restore exercises.
5. Protocol/security integration tests covering signed MDNs, replay, rollover, malformed messages, and tenant-boundary attempts.
