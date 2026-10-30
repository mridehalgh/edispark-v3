# MFT local development

The local stack starts with one deterministic, complete demo workspace. It is designed to exercise the same tenant and cryptographic boundaries as the production architecture while remaining disposable and requiring no AWS account.

## Identity and tenant mapping

The local Better Auth user belongs to the `EdiSpark Local` organization. Startup pins that organization to `LOCAL_SEED_TENANT_ID` in `organization_tenant`.

The mapping is one-to-one:

```text
local user -> EdiSpark Local organization <-> internal tenant ID
```

`organization_id` is the mapping table primary key and `tenant_id` is unique. Documents, MFT, JWT claims, and future tenant-aware local services must use that same internal tenant ID.

This does not mean one organization owns every physical KMS key. In production the internal tenant maps to one symmetric data-encryption key reused across approved services. Standard-tier AS2 signing and decryption use two separate asymmetric keys per deployment cell because KMS key usage is fixed and asymmetric calls have no encryption context.

## Demo fixture manifest

Local startup reconciles these stable examples:

| Area | Fixtures |
|---|---|
| Documents | TRADACOMS Order `PO-2026-0042` with a UBL derivative, UBL Invoice `INV-100045`, UBL Credit Note `CN-1007` |
| MFT partner | `Northstar Trading`, local AS2 ID `EDISPARK`, partner AS2 ID `SIMULATOR` |
| Certificates | One disposable local PKCS#12 identity and the simulator's public X.509 certificate |
| Transfers | Delivered inbound, delivered outbound, awaiting signed MDN, retry scheduled, and needs review |

Documents persistence is durable in local DynamoDB, so its seeder creates only missing stable business fixtures. MFT metadata is currently in memory, so its complete fixture is recreated on each MFT API start. Sample EDI uses generic placeholders and contains no real PII.

## Local KMS test double

`LocalKms` is a local-only adapter, not an AWS emulator and not a production security control. It provides the behavior the storage adapters need:

1. Load or create one random disposable root key below `.local/mft` with owner-only permissions.
2. Resolve `alias/edispark/tenant/{tenantId}/data` for the active tenant.
3. Derive a tenant-specific AES-256 key from the root and alias.
4. Encrypt with AES-GCM and authenticated context containing `tenant_id`, `service=mft`, `resource_type`, and `resource_id`.
5. Reject decryption under a different tenant or resource context.

The application does not select between local and AWS behavior inside its domain logic; profile-specific infrastructure implements the boundary. The current `phase2` integration still needs a disposable local PKCS#12 identity. It is encrypted through the local adapter, exists only below ignored `.local`, and must never become a production fallback.

## Sensitive local state

The following remains sensitive even though it is disposable:

- Better Auth secret, sessions, and SQLite database;
- the local KMS root key;
- generated PKCS#12 private key and password;
- EDI payloads and operational evidence;
- partner configuration and filenames.

All generated material stays below `.local`, is ignored by Git, and must not contain real customer data. `pnpm local:reset` deletes it and cannot recover user-created local records.

## Running and verification

Run `pnpm dev`, sign in with the documented local test account, and open `/mft`. The page should immediately show the partner, certificates, and five transfer scenarios. `/mft/docs` explains the production model; `http://localhost:8081/actuator/health` checks the MFT API.

For an isolated AS2 exchange, run `pnpm dev:as2-simulator`. The local endpoint is `http://localhost:8082/as2`, and private-network destinations are allowed only in local development.
