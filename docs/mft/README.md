# MFT and AS2 documentation

This folder is the production design record for the EdiSpark managed-file-transfer service. It separates three concerns that use AWS KMS differently:

1. tenant data-at-rest encryption;
2. AS2 signing and decryption identities;
3. public certificate lifecycle and partner interoperability.

## Decision summary

| Concern | Key strategy | Tenant boundary |
|---|---|---|
| EDI payloads, MDNs, confidential metadata | One symmetric customer-managed key per tenant, reused across EdiSpark services | Tenant-scoped alias, STS session policy, IAM conditions, and encryption context |
| AS2 message signing | One RSA `SIGN_VERIFY` KMS key per deployment cell, shared by standard-tier tenants | Exact AS2 identity routing, cell boundary, separate signing role, certificate version, and application authorization |
| AS2 message decryption | One RSA `ENCRYPT_DECRYPT` KMS key per deployment cell, shared by standard-tier tenants | Exact AS2 identity routing, cell boundary, separate decryption role, certificate version, and application authorization |
| Partner verification and outbound encryption | Partner public X.509 certificates | Tenant-owned metadata, immutable fingerprint, purpose, activation window |

The symmetric strategy follows the AWS Architecture Blog pattern of one customer-managed key **per tenant across services**. It does not propose one symmetric key for every tenant. Reusing the tenant key across services removes the `tenant × service` key explosion while retaining a tenant-specific revocation and deletion boundary.

A single symmetric key shared by every tenant is not adopted. Encryption context can bind symmetric ciphertext to a tenant, but one policy, disablement, deletion, quota, or key incident would then affect the entire platform. The per-tenant-across-services pattern is the intended cost/isolation compromise.

AS2 requires asymmetric keys. AWS KMS cannot use one RSA key for both signing and decryption, so the cost-conscious AS2 unit is a shared **two-key cell identity**, not one key. Asymmetric operations do not support KMS encryption context, which is why the AS2 keys are contained by cells and dedicated IAM roles rather than presented as tenant-cryptographic isolation.

## Documents

- [KMS key strategy](kms-key-strategy.md) — key hierarchy, aliases, authorization, costs, and lifecycle.
- [AS2 KMS compatibility](as2-kms-compatibility.md) — supported algorithms, certificate profile, OAEP limitation, and onboarding gate.
- [Tenant isolation](tenant-isolation.md) — control-plane and data-plane identity, storage boundaries, shared-key blast radius, and threat controls.
- [Operations runbook](operations-runbook.md) — provisioning, rotation, incidents, recovery, monitoring, and offboarding.
- [Sensitive-data inventory](../mft-security-and-operations.md) — current local behavior and all sensitive production records.

## Status

The local vertical slice uses `phase2`, disposable PKCS#12 identities, in-memory metadata, and encrypted files. These adapters are enabled only for `local` and `openapi-export`. Production has no fallback to local private-key files and cannot start until the KMS, DynamoDB, S3, and lease adapters are implemented.

## AWS references

- [Simplify multi-tenant encryption with a cost-conscious AWS KMS key strategy](https://aws.amazon.com/blogs/architecture/simplify-multi-tenant-encryption-with-a-cost-conscious-aws-kms-key-strategy/)
- [Asymmetric keys in AWS KMS](https://docs.aws.amazon.com/kms/latest/developerguide/symmetric-asymmetric.html)
- [AWS KMS encryption context](https://docs.aws.amazon.com/kms/latest/developerguide/encrypt_context.html)
- [AWS KMS Decrypt API and supported RSA algorithms](https://docs.aws.amazon.com/kms/latest/APIReference/API_Decrypt.html)
- [AWS KMS Sign API](https://docs.aws.amazon.com/kms/latest/APIReference/API_Sign.html)
