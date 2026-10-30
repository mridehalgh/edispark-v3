# AS2 connection details and multi-tenancy

The connection-details endpoint is the authenticated organisation-facing API behind the **Share your AS2 details** panel. It returns the values an organisation administrator gives to a retailer: the organisation's AS2 identifier, public receiving URLs, supported algorithms, and separate signing and encryption public certificates with fingerprints and expiry dates.

## Short answer

The current implementation supports multiple logical tenants in one MFT service process. A caller can receive only connection packs assembled from partner and certificate records belonging to the tenant in its validated JWT. The local in-memory adapter and behaviour tests exercise this isolation.

The production data plane uses DynamoDB tenant partitions and a cell-shared KMS identity. The application fails to start unless the two KMS keys and their matching public certificates are configured.

## Authenticated request path

```text
Organisation user
  -> Next.js /api/mft/connection-details BFF
  -> Better Auth access token
  -> MFT API validates JWT signature, issuer, audience and algorithm
  -> tenant_id claim establishes TenantContext
  -> ConnectionDetailsService lists partners for that tenant
  -> each certificate lookup repeats the same tenant ID
  -> response contains public onboarding information only
```

The browser never supplies a tenant ID, cell ID, certificate ID, KMS ARN, or backend URL as authority. The BFF obtains the access token from the authenticated server-side session. `MftTenantContextFilter` accepts only a validated JWT authentication, rejects a missing or blank `tenant_id`, and establishes the scoped tenant context for the request.

The organisation-to-tenant mapping is one-to-one. A user acts through an active organisation, Better Auth issues the corresponding internal `tenant_id`, and the MFT service uses that internal value as the data boundary. Changing a request body, query parameter, header, or browser URL cannot select a different tenant.

## Repository isolation

`ConnectionDetailsService.list()` reads the current tenant once from `TenantContext` and passes it to both repository operations:

1. `listPartners(tenantId)` returns only the tenant's partner profiles.
2. For every partner, `findCertificate(tenantId, localCertificateId)` resolves the organisation certificate under the same tenant.

The local repository keys tenant-owned records as:

```text
partner:     {tenantId}:{partnerId}
certificate: {tenantId}:{certificateId}
```

A corrupted or malicious partner record that references another tenant's certificate ID therefore receives “Organisation AS2 certificate was not found”; the service does not retry the lookup without the tenant key. `ConnectionDetailsServiceTest` covers both positive isolation and this cross-tenant reference failure.

The production DynamoDB adapter preserves the same rule with `TENANT#{tenantId}` partitions and consistent, tenant-qualified certificate reads. Authorization never depends on a filter applied after reading a cross-tenant result.

## What is shared and what is tenant-specific

| Value | Scope | Source |
|---|---|---|
| Partner name and partner ID | Tenant and partner | Tenant-scoped partner profile |
| Organisation AS2 identifier | Tenant and partner connection | Tenant-scoped partner profile |
| Public receiving URL | Deployment cell | Trusted deployment configuration |
| Asynchronous MDN URL | Deployment cell | Trusted deployment configuration |
| Advertised algorithms | Deployment cell/profile | Trusted deployment configuration |
| Certificate selection | Tenant identity binding | Tenant-scoped partner/identity configuration |
| Public certificate bytes and fingerprint | Certificate version | Public certificate registry or current local certificate record |
| Private signing/decryption operations | Deployment cell by default | Non-exportable AWS KMS keys and separate worker roles |

Public URLs and algorithm policy are currently common to every tenant served by a deployment cell. They are not inferred from the browser host because an internal control-plane hostname may differ from the public AS2 endpoint. Deployments must set `MFT_PUBLIC_AS2_URL`, `MFT_PUBLIC_AS2_MDN_URL`, and the advertised algorithm variables to values that are externally reachable and actually enabled.

## Cell-shared KMS identities

Standard tenants are intended to share a bounded cell's asymmetric KMS identity rather than receive two billable KMS keys each. This changes key custody, not tenant authorization.

The production runtime uses these bindings:

```text
tenant registry
  tenant_id -> cell_id

tenant AS2 identity binding
  tenant_id + AS2 identifier
  -> signing certificate version
  -> encryption certificate version

cell public certificate registry
  cell_id + purpose + version
  -> public certificate chain, fingerprint, validity and KMS key reference
```

The endpoint starts with the authenticated tenant and its tenant-owned partner profile. A caller never provides `cell_id`, a KMS key ARN, or a certificate version to select the response. Multiple tenants legitimately receive the same public cell encryption certificate, while each response remains authorized through that tenant's partner and local signing-certificate binding.

AWS KMS requires separate keys and certificates for signing and decryption. The production response exposes both purposes and distinct fingerprints/expiry dates. The local `phase2` bridge uses one disposable PKCS#12 identity, so local mode returns the same public certificate in both roles.

## Returned and excluded data

The endpoint may return:

- AS2 identifiers and public endpoint URLs;
- supported/required algorithm names;
- public X.509 certificate PEM, fingerprint, purpose and validity;
- the partner name needed to label the onboarding pack.

It must never return:

- PKCS#12/PFX material or passwords;
- private keys or decrypted branch material;
- KMS key IDs, ARNs, grants or IAM details;
- tenant branch IDs or encryption context;
- another tenant's partner, certificate or configuration records;
- access tokens or internal service URLs.

Although public certificates are not secret, the complete connection pack is tenant-scoped confidential configuration. Responses use private, no-store caching through the BFF and require normal organisation authorization.

## Failure behavior

| Condition | Result |
|---|---|
| Missing, invalid or expired authentication | `401` before domain access |
| Missing or blank `tenant_id` claim | `401` before domain access |
| No configured partners | Empty list for the authenticated tenant |
| Partner references a missing or cross-tenant certificate | Tenant-safe not-found error; no fallback lookup |
| MFT API unavailable | BFF returns a safe `502` message without infrastructure details |
| Cell certificate registry unavailable in production | Fail closed; never substitute a filesystem or another cell's certificate |

## Verification and remaining work

Current evidence:

- `ConnectionDetailsServiceTest` creates two tenants in one repository and proves tenant A receives only tenant A's pack.
- The same test proves a tenant A partner cannot resolve a tenant B certificate.
- The MFT API requires bearer authentication for `/api/mft/connection-details`.
- The BFF retains the access token server-side and returns `Cache-Control: private, no-store`.

Remaining activation work is per-tenant rate/concurrency limits, immutable certificate-version rollover, access/download audit events, and authorization tests with two real organisation tokens against deployed infrastructure.
