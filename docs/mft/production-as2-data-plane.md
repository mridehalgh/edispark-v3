# Production AS2 data plane

## Runtime boundary

The `production` Spring profile owns the public `POST /as2` and `POST /as2/mdn` routes. The Phase2 servlet and its PKCS#12 bridge remain restricted to `local` and `openapi-export`. There is no software-private-key fallback in production.

Production startup is fail-closed. It requires separate RSA KMS signing and decryption keys, a valid public X.509 certificate matching each KMS public key, the managed data stores, and externally reachable AS2 URLs. At startup the service calls `GetPublicKey` for both asymmetric keys and compares the returned public keys with the configured certificates. A missing, expired, malformed, unsupported, or mismatched identity stops the application before either route becomes available.

## Inbound `/as2`

1. The request must provide `AS2-From`, `AS2-To`, `Message-ID`, and `Content-Type`.
2. The exact, case-sensitive `(AS2-To, AS2-From)` pair resolves a globally unique owner record. The request cannot provide a tenant ID.
3. That record yields one tenant and one partner. Every subsequent certificate, transfer, payload, and replay lookup includes that tenant.
4. Encrypted CMS must use RSA-OAEP with SHA-256 and MGF1-SHA256. PKCS#1 v1.5 and OAEP-SHA1 are rejected before KMS is called.
5. KMS decrypts only the wrapped AES content key. The application never obtains the RSA private key.
6. The detached CMS signature must verify against the exact public certificate stored for the resolved partner.
7. `(tenant ID, Message-ID)` is written transactionally as the replay owner with the payload SHA-256. An exact retry returns the existing transfer; the same Message-ID with different bytes is rejected and cannot create a second transfer.
8. The service creates a SHA-256 MIC and signs the MDN through KMS. Synchronous MDNs are returned in the response. An asynchronous callback must exactly equal the HTTPS callback activated on the partner profile.
9. Before an asynchronous request is acknowledged, the exact signed MDN is written to tenant-encrypted payload storage and an idempotent outbox record is committed. A separate worker uses owner-conditional leases, bounded backoff, and eight attempts to deliver it.

Tenant administrators can inspect outbox status, attempt count, safe failure summary, and timestamps through `GET /api/mft/mdn-deliveries`; the MFT activity page surfaces exhausted receipts as work needing attention. The signed receipt bytes remain in encrypted payload storage and are not returned by that listing.

Unknown pairs, missing security layers, malformed MIME, certificate mismatch, MIC mismatch, and unsupported algorithms fail without a tenant fallback. Error bodies do not expose tenant, certificate, or key details.

Inbound AS2 identifiers and Message-IDs are syntax- and length-checked before repository access. Message-ID replay markers are tenant-qualified and bind the identifier to the payload digest: an exact retry returns the original result, while the same identifier with different bytes is rejected as a conflict even during a concurrent race.

Raw request bodies are bounded independently of Spring multipart settings. Both a declared `Content-Length` and the bytes actually read from a chunked request are limited. Authenticated messages that fail later protocol processing receive a signed negative MDN when a configured safe return route is available.

## Partner lifecycle and certificates

Production partners are created in `DRAFT`. Neither inbound nor outbound traffic is accepted until the tenant administrator activates the profile. Activation validates HTTPS endpoints, mandatory signing and encryption, disabled compression and legacy cryptography, tenant ownership of every certificate reference, current validity, RSA keys of at least 2048 bits, and certificate key usage when that extension is present. `SUSPENDED` profiles immediately stop accepting new traffic.

Partner signature verification and outbound encryption use separate certificate references. The references may select the same immutable uploaded certificate only when it is valid for both purposes. Rollover uploads a new immutable record and atomically selects the replacement pair only after the same activation validation succeeds. Outbound encryption switches immediately. Inbound signature verification accepts the retiring signing certificate only for the configured grace window (seven days by default), after which it fails closed. Retained transfers and receipts keep their historical fingerprints.

The application also provides instance-level load shedding: one global concurrency semaphore plus per-partnership concurrency and request-rate limits. This protects each process and prevents one known partnership consuming all parser/KMS work. Deployment-wide quotas and volumetric denial-of-service controls still belong at the edge and are infrastructure concerns.

## Outbound delivery and `/as2/mdn`

1. The authenticated control plane stores the payload once and creates a tenant-scoped queued transfer with an idempotency marker.
2. A worker acquires a DynamoDB conditional lease. Lease release is owner-conditional, so an old worker cannot delete a reclaimed lease. A `PROCESSING` record carries the same recovery deadline as the lease and becomes eligible for replay if the process dies mid-exchange. `AWAITING_MDN` is not continuously replayed while a legitimate asynchronous receipt is pending.
3. The adapter loads the partner certificate inside the transfer tenant and proves the profile's selected local certificate matches the configured KMS signing identity.
4. It hashes CMS signed attributes locally and calls `KMS Sign` with `MessageType=DIGEST`; plaintext EDI is not sent to KMS.
5. The signed entity uses AES-256-CBC content encryption and RSA-OAEP-SHA256 key transport to the partner certificate.
6. HTTP has a 10-second connect timeout and 30-second request deadline, does not follow redirects, and rejects private/link-local destinations unless the local-only override is enabled.
7. A synchronous signed MDN is verified against the exact partner certificate, original Message-ID, disposition, SHA-256 algorithm, and constant-time MIC comparison.
8. For asynchronous mode, the expected MIC is durably stored before the HTTP request is sent. `/as2/mdn` resolves the same AS2 pair, verifies the partner signature, looks up the original Message-ID only inside that tenant, proves the partner ID and MIC, and performs an idempotent terminal transition. Sender and callback state changes use a DynamoDB status condition, so a fast callback cannot be overwritten by a later `AWAITING_MDN`, retry, or review update.

Transport failures retry with bounded jitter. Protocol, identity, algorithm, signature, and MIC failures move the transfer to review.

CMS compression is deliberately rejected by the KMS-native path rather than being silently ignored. Keep `compressionEnabled=false` for production partners until compressed-data interoperability is implemented and qualified. The local Phase2 simulator can still exercise compressed legacy profiles.

## Tenant isolation

The global owner item contains only the routing reference needed to find a tenant and partner. Tenant-owned data stays below `TENANT#{tenantId}`. Outbound Message-ID markers are tenant-partitioned, so an MDN carrying another tenant's valid Message-ID cannot select that transfer. Partner ID and MIC checks provide two additional correlation boundaries.

The asymmetric KMS keys are shared by the deployment cell. This is cost isolation, not per-tenant physical-key isolation. Authorization comes from exact AS2-pair ownership, tenant-qualified storage, partner-specific certificate verification, separated IAM permissions, and the cell boundary.

## Production configuration

| Environment variable | Meaning |
|---|---|
| `MFT_AS2_SIGNING_KEY_ARN` | Cell RSA signing KMS key |
| `MFT_AS2_DECRYPTION_KEY_ARN` | Cell RSA OAEP decryption KMS key |
| `MFT_AS2_SIGNING_CERTIFICATE_BASE64` | Base64 DER public signing certificate |
| `MFT_AS2_ENCRYPTION_CERTIFICATE_BASE64` | Base64 DER public encryption certificate |
| `MFT_PUBLIC_AS2_URL` | Retailer-facing `/as2` URL |
| `MFT_PUBLIC_AS2_MDN_URL` | Retailer-facing `/as2/mdn` URL |
| `MFT_AS2_MAX_MESSAGE_BYTES` | Maximum raw `/as2` or `/as2/mdn` body; default 25 MiB |
| `MFT_AS2_MAX_OUTBOUND_PAYLOAD_BYTES` | Maximum outbound payload stream; default 100 MiB |
| `MFT_AS2_GLOBAL_CONCURRENCY` | Maximum concurrent AS2 work per process; default 64 |
| `MFT_AS2_PARTNER_CONCURRENCY` | Maximum concurrent work per partnership and process; default 8 |
| `MFT_AS2_PARTNER_REQUESTS_PER_MINUTE` | Per-partnership request window per process; default 600 |
| `MFT_AS2_CERTIFICATE_ROLLOVER_GRACE_DAYS` | Retiring partner signing-certificate verification window; default 7 days |

The two certificate values are public, integrity-critical configuration, not secrets. Private keys and PKCS#12 passwords are never configuration values. File/classpath certificate resources are also supported.

## Release evidence still required

No additional application feature is intentionally disabled. Before enabling any production partnership, however, complete bidirectional interoperability tests against deployed AWS KMS keys and the specific partner product, including signed MDNs, OAEP parameters, MIME canonicalisation, replay conflicts, malformed inputs, maximum file size, timeout/retry behaviour, and rollover. This is external acceptance evidence, not a software fallback. Terraform is defined and validated but has not been applied or deployed by this repository change.
