# MFT local development

The local stack starts with one deterministic, complete demo workspace. It is designed to exercise the same tenant and cryptographic boundaries as the production architecture while remaining disposable and requiring no AWS account. The repository-wide feature coverage manifest and completion gate live in [`docs/local-demo-data.md`](../local-demo-data.md).

## Identity and tenant mapping

The local Better Auth user belongs to the `EdiSpark Local` organization. Startup pins that organization to `LOCAL_SEED_TENANT_ID` in `organization_tenant`.

The mapping is one-to-one:

```text
local user -> EdiSpark Local organization <-> internal tenant ID
```

`organization_id` is the mapping table primary key and `tenant_id` is unique. Documents, MFT, JWT claims, and future tenant-aware local services must use that same internal tenant ID.

This does not mean one organization owns a physical KMS key. In production a standard tenant maps to one branch key under its cell's shared Hierarchical keyring root. Only an explicit dedicated-key policy creates a physical symmetric KMS key for that tenant. Standard-tier AS2 signing and decryption use two separate asymmetric keys per deployment cell because KMS key usage is fixed and asymmetric calls have no encryption context.

## Demo fixture manifest

Local startup reconciles these stable examples:

| Area | Fixtures |
|---|---|
| Documents | Inbound eight-line TRADACOMS Order `PO-2026-0042` with a UBL derivative, outbound matching UBL Invoice `INV-100045`, outbound UBL Credit Note `CN-1007` |
| MFT partners | EDIFACT retailer `Northstar Retail` (`SIMULATOR`) and TRADACOMS retailer `Harbour Retail` (`SIMULATOR_TRADACOMS`), local AS2 ID `EDISPARK` |
| Certificates | One disposable local PKCS#12 identity and the simulator's public X.509 certificate |
| Retailer onboarding | Copyable `EDISPARK` connection details and a downloadable public-only PEM certificate for each retailer connection |
| Transfers | Each retailer sends an eight-line order and receives a matching invoice; Northstar also demonstrates awaiting signed MDN, retry scheduled, and needs review |

Documents persistence is durable in local DynamoDB, so its seeder creates only missing stable business fixtures. MFT metadata is currently in memory, so its complete fixture is recreated on each MFT API start. Sample EDI uses generic placeholders and contains no real PII.

## Local KMS test double

`LocalKms` is a local-only adapter, not an AWS emulator and not a production security control. It provides the behavior the storage adapters need:

1. Load or create one random disposable Hierarchical root below `.local/mft` with owner-only permissions.
2. Resolve the deterministic local branch ID `tenant/{tenantId}` for the active tenant.
3. Derive branch and resource-scoped AES-256 material below that root.
4. Encrypt with AES-GCM and authenticated context containing `tenant_id`, `service=mft`, `resource_type`, and `resource_id`.
5. Reject decryption under a different tenant or resource context.

The application does not select between local and AWS behavior inside its domain logic; profile-specific infrastructure implements the boundary. The local adapter models isolation behavior, not AWS Encryption SDK ciphertext, DynamoDB branch persistence, per-message envelope keys, or caching. The current `phase2` integration still needs a disposable local PKCS#12 identity. It is encrypted through the local adapter, exists only below ignored `.local`, and must never become a production fallback.

## Sensitive local state

The following remains sensitive even though it is disposable:

- Better Auth secret, sessions, and SQLite database;
- the local KMS root key;
- generated PKCS#12 private key and password;
- EDI payloads and operational evidence;
- partner configuration and filenames.

All generated material stays below `.local`, is ignored by Git, and must not contain real customer data. `pnpm local:reset` deletes it and cannot recover user-created local records.

## Running and verification

Run `pnpm dev`. The runner starts the unified EdiSpark API, AS2 partner simulator, and web application in dependency order. Open **Local tools** in the lower-right corner to sign in as the deterministic local user. `/mft` immediately shows the partners, certificates, and seven transfer scenarios. `/mft/docs` explains the production model; `http://localhost:8080/actuator/health` checks the API.

Once authenticated, the shared development dock provides two live exchanges:

- **Receive order batch**: the simulator signs and encrypts `local-inbound-orders-batch.edi`, sends its two ORDERS messages to the MFT AS2 receiver, and captures the signed MDN.
- **Send invoice**: the MFT API queues `local-outbound-invoice.edi`; the delivery worker sends it to the simulator and records the MDN evidence.

The dock shows the simulator peer's latest observations. The MFT transfer log remains the authoritative tenant-scoped evidence record. The Trading partners tab also exposes the local organisation's retailer-facing connection pack through the authenticated `/api/mft/connection-details` BFF. It contains public connection metadata and an X.509 public certificate only. The PKCS#12 bytes and password never enter the response or browser. Both the dock and `/api/mft/local-simulator` are unavailable when `NODE_ENV` is not `development`. The BFF keeps the MFT bearer token and simulator URL out of browser JavaScript.

The advertised URLs and algorithms are deployment configuration rather than browser-derived values:

| Environment variable | Local default |
|---|---|
| `MFT_PUBLIC_AS2_URL` | `http://localhost:8080/as2` |
| `MFT_PUBLIC_AS2_MDN_URL` | `http://localhost:8080/as2/mdn` |
| `MFT_SUPPORTED_ENCRYPTION_ALGORITHMS` | `AES256-CBC` |
| `MFT_SUPPORTED_SIGNATURE_ALGORITHMS` | `SHA-256` |
| `MFT_KEY_ENCRYPTION_ALGORITHM` | `RSA-OAEP-SHA256` |

Production values must be the externally reachable endpoints and the algorithms actually enabled by the deployed AS2/KMS adapter. The local `phase2` bridge uses one disposable identity certificate. The KMS-native production profile remains defined by separate signing and encryption certificates, both of which must be shared during retailer onboarding once that adapter is implemented.

For an isolated peer process, run `pnpm dev:as2-simulator`. The local endpoint is `http://localhost:8082/as2`, and private-network destinations are allowed only in local development.
