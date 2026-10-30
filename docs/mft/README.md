# MFT and AS2 documentation

This folder is the production design record for the EdiSpark managed-file-transfer service. It separates three concerns that use AWS KMS differently:

1. tenant data-at-rest encryption;
2. AS2 signing and decryption identities;
3. public certificate lifecycle and partner interoperability.

These repository documents are written for EdiSpark engineering, security, and platform operations. The in-product `/mft/docs` setup guide is deliberately different: it is written for an organisation administrator configuring and monitoring their own trading-partner connections. KMS topology, deployment-cell controls, IAM, and shared-service incident response belong here rather than in the customer workspace. The customer guide retains only technical constraints that change onboarding decisions, such as RSA-OAEP support and separate signing and encryption certificates.

## Decision summary

| Concern | Key strategy | Tenant boundary |
|---|---|---|
| EDI payloads, MDNs, confidential metadata | Default: one tenant branch key under a cell-shared Hierarchical keyring root; explicit override: dedicated tenant KMS key | Trusted tenant-to-branch registry, authenticated context, tenant storage authorization, and cell boundary |
| AS2 message signing | One RSA `SIGN_VERIFY` KMS key per deployment cell, shared by standard-tier tenants | Exact AS2 identity routing, cell boundary, separate signing role, certificate version, and application authorization |
| AS2 message decryption | One RSA `ENCRYPT_DECRYPT` KMS key per deployment cell, shared by standard-tier tenants | Exact AS2 identity routing, cell boundary, separate decryption role, certificate version, and application authorization |
| Partner verification and outbound encryption | Partner public X.509 certificates | Tenant-owned metadata, immutable fingerprint, purpose, activation window |

The default symmetric strategy uses the AWS Encryption SDK Hierarchical keyring. Each bounded deployment cell has one symmetric KMS root and one DynamoDB key store; every tenant has a distinct branch key. The SDK derives unique wrapping and data keys per encryption operation and caches branch materials, avoiding a billable KMS key and KMS call for every tenant payload. A tenant gets a dedicated KMS key only when an operator explicitly selects that policy.

This is not equivalent to independent physical KMS isolation. Root-key, key-store, cache, quota, or policy failures can affect the cell. Cell boundaries, trusted branch selection, DynamoDB recovery controls, short bounded cache TTLs, per-tenant admission controls, and an opt-in dedicated-key tier contain that tradeoff.

AS2 requires asymmetric keys. AWS KMS cannot use one RSA key for both signing and decryption, so the cost-conscious AS2 unit is a shared **two-key cell identity**, not one key. Asymmetric operations do not support KMS encryption context, which is why the AS2 keys are contained by cells and dedicated IAM roles rather than presented as tenant-cryptographic isolation.

## Documents

- [KMS key strategy](kms-key-strategy.md) — key hierarchy, aliases, authorization, costs, and lifecycle.
- [AS2 KMS compatibility](as2-kms-compatibility.md) — supported algorithms, certificate profile, OAEP limitation, and onboarding gate.
- [Tenant isolation](tenant-isolation.md) — control-plane and data-plane identity, storage boundaries, shared-key blast radius, and threat controls.
- [Operations runbook](operations-runbook.md) — provisioning, rotation, incidents, recovery, monitoring, and offboarding.
- [Local development](local-development.md) — deterministic demo identity, fixture manifest, local KMS test double, and reset behavior.
- [Sensitive-data inventory](../mft-security-and-operations.md) — current local behavior and all sensitive production records.

## Status

The local vertical slice uses `phase2`, disposable PKCS#12 identities, in-memory metadata, and tenant/resource-bound encrypted files. A behavior-level test double selects tenant branches beneath one disposable local root without calling AWS. It does not implement AWS ciphertext, DynamoDB branch persistence, unique envelope data keys, or caching. These adapters are enabled only for `local` and `openapi-export`. Production has no fallback to local private-key files and cannot start until the KMS, DynamoDB, S3, and lease adapters are implemented.

## AWS references

- [Simplify multi-tenant encryption with a cost-conscious AWS KMS key strategy](https://aws.amazon.com/blogs/architecture/simplify-multi-tenant-encryption-with-a-cost-conscious-aws-kms-key-strategy/)
- [Asymmetric keys in AWS KMS](https://docs.aws.amazon.com/kms/latest/developerguide/symmetric-asymmetric.html)
- [AWS KMS encryption context](https://docs.aws.amazon.com/kms/latest/developerguide/encrypt_context.html)
- [AWS KMS Hierarchical keyrings](https://docs.aws.amazon.com/encryption-sdk/latest/developer-guide/use-hierarchical-keyring.html)
- [AWS Encryption SDK key stores](https://docs.aws.amazon.com/encryption-sdk/latest/developer-guide/keystores.html)
- [AWS KMS Decrypt API and supported RSA algorithms](https://docs.aws.amazon.com/kms/latest/APIReference/API_Decrypt.html)
- [AWS KMS Sign API](https://docs.aws.amazon.com/kms/latest/APIReference/API_Sign.html)
