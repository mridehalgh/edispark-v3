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
7. `(tenant ID, Message-ID, payload SHA-256)` is written transactionally as the replay marker. A retry returns the existing transfer instead of storing a second file.
8. The service creates a SHA-256 MIC and signs the MDN through KMS. Synchronous MDNs are returned in the response. An asynchronous callback is allowed only for an asynchronous partnership and only on the same scheme, host, and effective port as the configured partner endpoint.

Unknown pairs, missing security layers, malformed MIME, certificate mismatch, MIC mismatch, and unsupported algorithms fail without a tenant fallback. Error bodies do not expose tenant, certificate, or key details.

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

The two certificate values are public, integrity-critical configuration, not secrets. Private keys and PKCS#12 passwords are never configuration values. File/classpath certificate resources are also supported.

## Release evidence still required

Before enabling any production partnership, complete bidirectional interoperability tests against deployed AWS KMS keys, including signed MDNs, OAEP parameters, MIME canonicalisation, replay, malformed inputs, maximum file size, timeout/retry behaviour, and rollover. Terraform is defined and validated but has not been applied or deployed by this repository change.
