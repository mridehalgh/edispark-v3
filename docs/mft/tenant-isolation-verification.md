# MFT tenant-isolation verification

## Security invariant

An authenticated organisation can only access partners, certificates, transfers, and payloads belonging to its validated JWT tenant. A public AS2 request cannot nominate a tenant: the exact, case-sensitive `AS2-To` + `AS2-From` pair resolves to one globally owned partnership. Unknown or ambiguous pairs fail closed.

## Enforcement layers

| Layer | Enforcement |
|---|---|
| Control-plane HTTP | Tenant comes from the validated JWT; missing or blank claims are rejected |
| Application | Partner and certificate lookups always include the current tenant |
| AS2 ingress | A global AS2-pair ownership record resolves one tenant and partner |
| DynamoDB | Tenant records use `pk = TENANT#{tenantId}` and key-condition queries, never scans or tenant filters |
| Partner creation | One conditional transaction claims the global pair and writes the tenant partner |
| Replay safety | Tenant-scoped idempotency and Message-ID + payload-hash markers are transactional |
| S3 | Keys start with `{tenantId}/`; mismatches fail before S3 is called |
| Encryption | Context binds `tenant`, `service`, `purpose`, and `object-key` |
| Hierarchical keyring | The tenant registry resolves exactly one branch key; missing mappings fail closed |

The metadata table needs partition key `pk`, sort key `sk`, and a `work-index` GSI using `workPk` and `workSk`. The AWS Encryption SDK key-store table is separate and uses `branch-key-id` and `type`. Never delete the key-store table; its loss makes existing ciphertext unrecoverable.

## Adversarial tests

The suite rejects cross-tenant certificate IDs, wrong certificate purposes, duplicate AS2-pair ownership, inbound misrouting, unknown-pair fallback, another tenant's outbound partner, shared idempotency keys, foreign payload/secret references, absent branch mappings, non-tenant DynamoDB reads, filtered tenant lists, and non-atomic pair ownership. Property tests repeatedly exercise isolation, replay deduplication, idempotency, integrity, and terminal states.

Run it with:

```bash
./mvnw -B -pl domains/mft -am test
```

## Sensitive records

Production S3 stores sensitive EDI and protocol evidence only as AWS Encryption SDK ciphertext, plus non-secret format and SHA-256 metadata. DynamoDB stores tenant IDs, AS2 IDs, endpoint URLs, transfer metadata, public certificate chains/fingerprints, and opaque object references. The key store contains KMS-protected branch keys; CloudTrail contains KMS audit metadata. Production rejects PKCS#12/private-key uploads before parsing or storage. Local simulator private material is encrypted under `.local/`, is disposable, and must never be copied to production.

## Release gate

Before enabling a partner, run two-tenant canary exchanges in both directions, replay a Message-ID, attempt cross-tenant API IDs and S3 references, verify signed synchronous and asynchronous MDNs, and inspect DynamoDB, S3, KMS, and CloudTrail evidence. KMS-native AS2 is incompatible with RSA PKCS#1 v1.5 key transport; the partner must prove RSA-OAEP support.
