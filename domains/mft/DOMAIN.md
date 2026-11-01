# Managed File Transfer

## Vision

Managed File Transfer gives every AS2 exchange a verifiable, tenant-isolated chain of evidence. It protects protocol security and delivery truth from being diluted into document parsing or UI concerns.

## Overview

The domain owns local AS2 identities, trading-partner transport profiles, certificate metadata, transfers, attempts, MDNs, payload references, and resend lineage. It does not parse EDI business documents or deliver files through SFTP or object-storage routes.

After an inbound payload is durably retained, MFT publishes a metadata-only `MftInboundPayloadReceived` event. Payload bytes remain behind the tenant-scoped MFT boundary and are available to the trusted Integration consumer through an authenticated internal content route.

## Narrative

An integration analyst registers the workspace AS2 identity and a partner's endpoint and purpose-separated signing/encryption certificates. A new production profile is a draft until activation proves its secure policy; suspension removes it from both directions. The organisation administrator can then share the tenant-scoped AS2 identifier, public receiving URLs, supported algorithms, certificate fingerprints, and public certificates with that partner. Private key material is never part of the onboarding pack. When an operations user submits a file, the service records one idempotent transfer, stores immutable bytes, prepares an AS2 message, and waits for verifiable receipt evidence. Incoming messages are matched by the exact AS2 identities, authenticated, stored once, and acknowledged. An exact Message-ID replay returns the original result, while different bytes under the same Message-ID are rejected. Asynchronous signed receipts enter a durable, leased retry outbox before acknowledgement. A manual resend is a new, linked exchange so the original evidence never changes.

AS2 profiles use stable families and immutable revisions, with immediate or scheduled activation and explicit archival. Only unused drafts can be deleted. Certificates are immutable security records: rotation creates a new record, and deletion is permitted only when no AS2 profile references the certificate or its secret material.

## Boundaries

The authenticated control plane obtains the workspace from the validated JWT `tenant_id`. The public AS2 data plane has no user token and resolves one workspace from the exact, case-sensitive `(AS2-To, AS2-From)` pair. That pair is globally unique. After resolution, every partner, certificate, transfer, payload, and evidence operation is scoped to the resolved tenant.

The domain stores certificate lifecycle metadata and references to key custody. Production private-key operations belong behind a managed crypto port; raw private keys are not domain data. Partner and EdiSpark public X.509 certificate chains remain stored because they are required for verification, encryption, expiry monitoring, and rollover.

## Production posture

The local profile uses in-memory metadata and encrypted files for disposable test identities and payloads. Production composes DynamoDB ownership/leases, tenant-encrypted S3 payloads, the Hierarchical keyring, and KMS-native CMS signing/RSA-OAEP decryption; it fails closed when any managed dependency or certificate/key binding is absent. The production key hierarchy, tenant isolation, compatibility profile, operations, and sensitive-data inventory are documented in [`docs/mft`](../../docs/mft/README.md).
