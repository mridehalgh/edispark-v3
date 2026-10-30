# MFT AS2 design

## Architecture

`domains/mft` owns the domain, API controllers, application services, and adapters. `apps/mft-api` is the deployable composition root. Protocol implementation is isolated behind `As2ExchangePort`; payload and secret storage are behind streaming ports. `tooling/as2-simulator` is a separate loopback-only peer.

The control plane uses OIDC and tenant context. The AS2 data plane is public and resolves the tenant by exact `AS2-To`, then validates the configured partner and certificate. Data-plane processing uses the same repository ports as the control plane.

## Consistency and retries

Outbound idempotency is guarded atomically by `(tenantId, idempotencyKey)`. Inbound deduplication uses `(tenantId, messageId, payload SHA-256)`. The local worker polls due transfers and applies bounded retries with jitter. A production metadata adapter must add distributed conditional leases before horizontally scaling workers.

## Storage

Repository ports isolate metadata persistence. The implemented local profile keeps metadata in memory and stores immutable payload and private-key bytes beneath `.local/mft` with AES-GCM and restrictive permissions. A production DynamoDB adapter remains follow-up work; large payload values must stay outside DynamoDB.

Local persistence and PKCS#12 material are enabled only for the `local` and `openapi-export` profiles. Production must fail closed until managed adapters are supplied: DynamoDB for tenant metadata and leases, S3/SSE-KMS for payload/evidence, and AWS-managed private-key operations.

## Production key custody

KMS-native AS2 uses separate non-exportable RSA keys for signing and decryption and requires RSA-OAEP-compatible partner profiles. The existing `phase2` PKCS#12 adapter is local-only; the production implementation needs a signer/decrypter port that passes message digests or ciphertext to AWS without loading key bytes. Standard tenants share one signing/decryption key pair within a bounded deployment cell. PKCS#1 v1.5 partners are incompatible and cannot be activated; no alternative private-key provider or local fallback is supported.

Remote partner and local public X.509 certificate chains remain tenant-scoped stored records. They are not confidential, but their integrity, purpose, validity, fingerprint, and rotation state are security-critical. The complete decision is in `docs/mft/` and the sensitive-data inventory is in `docs/mft-security-and-operations.md`.

## Security

The service limits multipart request size, normalizes filenames, blocks outbound private-network targets by default, keeps bearer tokens in the BFF, encrypts local material at rest, and uses explicit outbound timeouts. SHA-256, AES-GCM, signing, encryption, compression, and signed MDNs are defaults. Legacy algorithms require both a service-level gate and a partner-level override.

An inbound `(AS2-To, AS2-From)` identity pair belongs to exactly one tenant. Local partner creation rejects duplicate ownership; the production repository must enforce ownership atomically. No production adapter may fall back to filesystem keys when managed crypto is unavailable.

## Correctness properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Outbound idempotence

*For any* tenant, partner, payload, and idempotency key, repeated equivalent submissions produce one transfer and the same response.

**Validates: Requirements 3.1**

### Property 2: Inbound deduplication

*For any* valid inbound message, receiving the same identity and MIC more than once stores one payload and returns the same outcome.

**Validates: Requirements 3.2, 3.3**

### Property 3: Tenant isolation

*For any* two distinct tenants, resources written under one tenant cannot be returned from queries executed under the other.

**Validates: Requirements 1.1, 1.2, 1.3**

### Property 4: Transfer state safety

*For any* transfer and event sequence, only declared state transitions are accepted and delivered transfers never transition back to active delivery.

**Validates: Requirements 3.4, 3.5, 3.6**

### Property 5: Payload integrity

*For any* accepted payload, reading the stored content returns the same bytes and SHA-256 hash.

**Validates: Requirements 4.1, 4.2**

## Testing strategy

JUnit tests cover examples and port failures. jqwik properties run at least 100 tries with constrained generators for tenant IDs, AS2 IDs, message IDs, byte payloads, and transfer events. Protocol integration tests use the local simulator or embedded HTTP servers; Testcontainers are prohibited. Frontend tests use Vitest and accessible DOM queries.

